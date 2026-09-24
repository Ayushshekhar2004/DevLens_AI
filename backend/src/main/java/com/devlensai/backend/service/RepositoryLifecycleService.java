package com.devlensai.backend.service;

import com.devlensai.backend.config.RepositoryLifecycleProperties;
import com.devlensai.backend.dto.*;
import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.RepositoryImportException;
import com.devlensai.backend.exception.RepositorySnapshotNotFoundException;
import com.devlensai.backend.repository.RepositoryJobRepository;
import com.devlensai.backend.repository.RepositoryScanRepository;
import com.devlensai.backend.repository.RepositorySnapshotRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

@Service
public class RepositoryLifecycleService {
    private static final int MAX_PAGE_SIZE = 50;
    private final RepositoryImportService imports;
    private final RepositoryScanService scans;
    private final RepositoryJobStateService jobState;
    private final RepositorySnapshotRepository snapshots;
    private final RepositoryScanRepository scanRepository;
    private final RepositoryJobRepository jobRepository;
    private final RepositoryLifecycleProperties properties;
    private final RepositoryAnalysisOrchestrator analysisOrchestrator;
    private final ThreadPoolTaskExecutor executor;
    private final Map<Long, Future<?>> running = new ConcurrentHashMap<>();

    public RepositoryLifecycleService(RepositoryImportService imports, RepositoryScanService scans,
                                      RepositoryJobStateService jobState, RepositorySnapshotRepository snapshots,
                                      RepositoryScanRepository scanRepository, RepositoryJobRepository jobRepository,
                                      RepositoryLifecycleProperties properties, RepositoryAnalysisOrchestrator analysisOrchestrator,
                                      @Qualifier("repositoryTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.imports = imports;
        this.scans = scans;
        this.jobState = jobState;
        this.snapshots = snapshots;
        this.scanRepository = scanRepository;
        this.jobRepository = jobRepository;
        this.properties = properties;
        this.analysisOrchestrator = analysisOrchestrator;
        this.executor = executor;
    }

    public RepositoryImportLifecycleResponse importAndQueue(User user, MultipartFile upload) {
        String sourceName = safeName(upload == null ? null : upload.getOriginalFilename());
        RepositoryJob importJob = jobState.create(user, null, RepositoryJobType.IMPORT, sourceName);
        try {
            jobState.markRunning(importJob.getId());
            RepositorySnapshotResponse snapshotResponse = imports.importZip(user, upload);
            RepositoryJob completed = jobState.markCompleted(importJob.getId(), snapshotResponse.id());
            RepositoryJobResponse scanJob = queueScan(user, snapshotResponse.id());
            return new RepositoryImportLifecycleResponse(snapshotResponse.id(), RepositoryJobResponse.from(completed), scanJob);
        } catch (RuntimeException exception) {
            jobState.markFailed(importJob.getId(), safeFailure(exception));
            throw exception;
        }
    }

    public synchronized RepositoryJobResponse queueScan(User user, Long snapshotId) {
        RepositorySnapshot snapshot = ownedSnapshot(user, snapshotId);
        Optional<RepositoryJob> duplicate = jobState.activeOrCompletedScan(snapshotId);
        if (duplicate.isPresent()) return RepositoryJobResponse.from(duplicate.get());
        RepositoryJob job = jobState.create(user, snapshot, RepositoryJobType.SCAN, snapshot.getSourceName());
        try {
            Future<?> future = executor.submit(() -> executeScan(job.getId(), user, snapshotId));
            running.put(job.getId(), future);
        } catch (RejectedExecutionException exception) {
            jobState.markFailed(job.getId(), "Repository scan queue is full; retry later");
        }
        return RepositoryJobResponse.from(jobState.owned(job.getId(), user.getId()));
    }

    public RepositoryJobResponse job(User user, Long jobId) {
        return RepositoryJobResponse.from(jobState.owned(jobId, user.getId()));
    }

    public RepositoryJobResponse cancel(User user, Long jobId) {
        RepositoryJob job = jobState.requestCancel(jobId, user.getId());
        Future<?> future = running.get(jobId);
        if (future != null) future.cancel(true);
        if (job.getStatus() == RepositoryJobStatus.RUNNING || job.getStatus() == RepositoryJobStatus.QUEUED) {
            job = jobState.markCancelled(jobId);
        }
        return RepositoryJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public RepositoryInventoryResponse.Page<RepositoryInventoryResponse.Summary> list(User user, int page, int size) {
        validatePage(page, size);
        var result = snapshots.findAllByUserId(user.getId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<RepositoryInventoryResponse.Summary> content = result.getContent().stream().map(snapshot -> summary(user, snapshot)).toList();
        return new RepositoryInventoryResponse.Page<>(content, page, size, result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public RepositoryInventoryResponse.Detail detail(User user, Long snapshotId, int page, int size) {
        validatePage(page, size);
        RepositorySnapshot snapshot = ownedSnapshot(user, snapshotId);
        Optional<RepositoryScan> scan = scanRepository.findBySnapshotIdAndUserId(snapshotId, user.getId());
        if (scan.isEmpty()) return new RepositoryInventoryResponse.Detail(summary(user, snapshot), List.of(),
                new RepositoryInventoryResponse.Page<>(List.of(), page, size, 0, 0, true, true));
        List<RepositoryScanResponse.FileMetadata> all = scan.get().getFiles().stream()
                .map(file -> new RepositoryScanResponse.FileMetadata(file.relativePath(), file.language(), file.contentHash(),
                        file.lineCount(), file.parserStatus(), file.parserMode(), file.moduleRoot())).toList();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = all.isEmpty() ? 0 : (all.size() + size - 1) / size;
        return new RepositoryInventoryResponse.Detail(summary(user, snapshot), scan.get().getModules(),
                new RepositoryInventoryResponse.Page<>(all.subList(from, to), page, size, all.size(), totalPages,
                        page == 0, totalPages == 0 || page >= totalPages - 1));
    }

    @Transactional
    public void delete(User user, Long snapshotId) {
        RepositorySnapshot snapshot = ownedSnapshot(user, snapshotId);
        deleteSnapshot(snapshot);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() { jobState.recoverInterrupted(); }

    @Scheduled(fixedDelayString = "${app.repository-lifecycle.cleanup-interval-hours}h")
    @Transactional
    public void enforceRetention() {
        if (properties.retentionDays() == 0) return;
        Instant cutoff = Instant.now().minus(properties.retentionDays(), ChronoUnit.DAYS);
        snapshots.findByCreatedAtBefore(cutoff)
                .forEach(this::deleteSnapshot);
        jobRepository.deleteBySnapshotIsNullAndCreatedAtBefore(cutoff);
    }

    private void executeScan(Long jobId, User user, Long snapshotId) {
        try {
            jobState.markRunning(jobId);
            scans.scanOwned(user, snapshotId, () -> jobState.cancellationRequested(jobId));
            if (jobState.cancellationRequested(jobId) || Thread.currentThread().isInterrupted()) jobState.markCancelled(jobId);
            else jobState.markCompleted(jobId, snapshotId);
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted() || safeFailure(exception).toLowerCase(Locale.ROOT).contains("cancel")) {
                jobState.markCancelled(jobId);
            } else jobState.markFailed(jobId, safeFailure(exception));
        } finally {
            running.remove(jobId);
        }
    }

    private void deleteSnapshot(RepositorySnapshot snapshot) {
        Long snapshotId = snapshot.getId();
        analysisOrchestrator.deleteForSnapshot(snapshotId);
        running.entrySet().removeIf(entry -> {
            RepositoryJob job = jobRepository.findById(entry.getKey()).orElse(null);
            if (job != null && job.getSnapshot() != null && snapshotId.equals(job.getSnapshot().getId())) {
                entry.getValue().cancel(true); return true;
            }
            return false;
        });
        jobRepository.deleteBySnapshotId(snapshotId);
        scanRepository.deleteBySnapshotId(snapshotId);
        snapshots.delete(snapshot);
        imports.deleteStorage(snapshot.getStorageKey());
    }

    private RepositoryInventoryResponse.Summary summary(User user, RepositorySnapshot snapshot) {
        Optional<RepositoryScan> scan = scanRepository.findBySnapshotIdAndUserId(snapshot.getId(), user.getId());
        Optional<RepositoryJob> job = jobState.activeOrCompletedScan(snapshot.getId());
        String scanStatus = scan.isPresent() ? "COMPLETED" : job.map(value -> value.getStatus().name()).orElse("NOT_STARTED");
        if (scan.isEmpty()) return new RepositoryInventoryResponse.Summary(snapshot.getId(), snapshot.getSourceName(),
                snapshot.getCreatedAt(), snapshot.getTotalBytes(), snapshot.getFiles().size(), "COMPLETED", scanStatus,
                "", 0, 0, Map.of(), 0);
        RepositoryScan value = scan.get();
        Map<String, Long> reasons = new TreeMap<>();
        value.getSkips().forEach(skip -> reasons.merge(skip.reason(), 1L, Long::sum));
        long parsed = value.getFiles().stream().filter(file -> file.parserStatus().equals("PARSED")).count();
        double coverage = value.getFiles().isEmpty() ? 0 : Math.round(parsed * 1000.0 / value.getFiles().size()) / 10.0;
        return new RepositoryInventoryResponse.Summary(snapshot.getId(), snapshot.getSourceName(), snapshot.getCreatedAt(),
                snapshot.getTotalBytes(), snapshot.getFiles().size(), "COMPLETED", scanStatus, value.getStackSummary(),
                value.getFiles().size(), value.getSkips().size(), reasons, coverage);
    }

    private RepositorySnapshot ownedSnapshot(User user, Long id) {
        return snapshots.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new RepositorySnapshotNotFoundException(id));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new RepositoryImportException("Page must be non-negative and size must be between 1 and 50");
    }

    private String safeFailure(RuntimeException exception) {
        if (exception instanceof RepositoryImportException
                || exception instanceof com.devlensai.backend.exception.RepositoryScanException) {
            return exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "Repository operation failed safely" : exception.getMessage();
        }
        return "Repository operation failed safely";
    }

    private String safeName(String provided) {
        if (provided == null || provided.isBlank()) return "repository.zip";
        String normalized = provided.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return name.substring(0, Math.min(255, name.length()));
    }
}
