package com.devlensai.backend.service;

import com.devlensai.backend.dto.RepositoryAnalysisJobResponse;
import com.devlensai.backend.dto.RepositorySummaryResponse;
import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.RepositorySnapshotNotFoundException;
import com.devlensai.backend.repository.RepositoryAnalysisJobRepository;
import com.devlensai.backend.repository.RepositoryAnalysisStageRepository;
import com.devlensai.backend.repository.RepositoryAnalysisUnitRepository;
import com.devlensai.backend.repository.RepositorySummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
public class RepositoryAnalysisStateService {
    private final RepositoryAnalysisJobRepository jobs;
    private final RepositoryAnalysisStageRepository stages;
    private final RepositoryAnalysisUnitRepository units;
    private final RepositorySummaryRepository summaries;

    public RepositoryAnalysisStateService(RepositoryAnalysisJobRepository jobs,
            RepositoryAnalysisStageRepository stages, RepositoryAnalysisUnitRepository units,
            RepositorySummaryRepository summaries) {
        this.jobs = jobs; this.stages = stages; this.units = units; this.summaries = summaries;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositoryAnalysisJob create(User user, RepositorySnapshot snapshot, String snapshotHash,
            String provider, String profile, String model, String promptVersion, String parserVersion,
            String schemaVersion, Instant deadline) {
        RepositoryAnalysisJob job = jobs.save(new RepositoryAnalysisJob(user, snapshot, snapshotHash, provider,
                profile, model, promptVersion, parserVersion, schemaVersion, deadline));
        for (RepositoryAnalysisStageType type : RepositoryAnalysisStageType.values()) stages.save(new RepositoryAnalysisStage(job, type));
        return job;
    }

    @Transactional(readOnly = true)
    public Optional<RepositoryAnalysisJob> idempotent(Long snapshotId, String profile, String model, String hash) {
        return jobs.findFirstBySnapshotIdAndProfileIdAndModelNameAndSnapshotHashAndStatusInOrderByCreatedAtDesc(
                snapshotId, profile, model, hash, List.of(RepositoryAnalysisJobStatus.QUEUED,
                        RepositoryAnalysisJobStatus.RUNNING, RepositoryAnalysisJobStatus.COMPLETED,
                        RepositoryAnalysisJobStatus.PARTIAL));
    }

    @Transactional(readOnly = true)
    public RepositoryAnalysisJob owned(Long id, Long userId) {
        return jobs.findByIdAndUserId(id, userId).orElseThrow(() -> new RepositorySnapshotNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public RepositoryAnalysisJobResponse response(Long id, Long userId) {
        return RepositoryAnalysisJobResponse.from(owned(id, userId), stages.findByJobIdOrderByIdAsc(id));
    }

    @Transactional(readOnly = true)
    public ExecutionContext context(Long id) {
        RepositoryAnalysisJob job = required(id);
        return new ExecutionContext(job.getUser(), job.getSnapshot().getId(), job.getSnapshotHash(),
                job.getProfileId(), job.getModelName(), job.getParserVersion(), job.getPromptVersion(),
                job.getSchemaVersion(), job.getDeadlineAt(), job.getUsedCalls(), job.getUsedInputTokens(),
                job.getUsedOutputTokens(), job.getStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stageRunning(Long jobId, RepositoryAnalysisStageType type) {
        RepositoryAnalysisJob job = requiredForUpdate(jobId); job.running(type); stage(jobId, type).running();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stageCompleted(Long jobId, RepositoryAnalysisStageType type) { stage(jobId, type).completed(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUnits(Long jobId, List<UnitSpec> specs) {
        RepositoryAnalysisJob job = requiredForUpdate(jobId);
        if (!units.findByJobIdOrderBySequenceAsc(jobId).isEmpty()) return;
        int skipped = 0;
        for (UnitSpec spec : specs) {
            RepositoryAnalysisUnit unit = new RepositoryAnalysisUnit(job, spec.chunkId(), spec.relativePath(),
                    spec.fileHash(), spec.startLine(), spec.endLine(), spec.sequence(), spec.inputTokens());
            if (spec.skipReason() != null) { unit.skipped(spec.skipReason()); skipped++; }
            units.save(unit);
        }
        job.unitsCreated(specs.size(), skipped);
    }

    @Transactional(readOnly = true)
    public List<RepositoryAnalysisUnit> units(Long jobId) { return units.findByJobIdOrderBySequenceAsc(jobId); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginUnit(Long jobId, Long unitId, int inputTokens) {
        RepositoryAnalysisJob job = requiredForUpdate(jobId);
        RepositoryAnalysisUnit unit = units.findById(unitId).orElseThrow();
        if (unit.getStatus() != RepositoryAnalysisUnitStatus.PENDING) return;
        unit.running(); job.consumeCall(inputTokens);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeUnit(Long jobId, Long unitId, String summary, int outputTokens) {
        RepositoryAnalysisJob job = requiredForUpdate(jobId);
        RepositoryAnalysisUnit unit = units.findById(unitId).orElseThrow();
        if (unit.getStatus() != RepositoryAnalysisUnitStatus.RUNNING) return;
        unit.completed(summary, outputTokens); job.unitCompleted(outputTokens);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeSummaryCall(Long jobId, int inputTokens) { requiredForUpdate(jobId).consumeCall(inputTokens); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeSummaryOutput(Long jobId, int outputTokens) { requiredForUpdate(jobId).consumeOutput(outputTokens); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositorySummary saveSummary(Long jobId, RepositorySummaryLevel level, String identity,
            String contentIdentity, String cacheKey, com.devlensai.backend.dto.RepositorySummaryResult result,
            boolean cacheHit) {
        RepositoryAnalysisJob job = requiredForUpdate(jobId);
        return summaries.save(new RepositorySummary(job, job.getUser(), level, identity, contentIdentity, cacheKey, result, cacheHit));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositorySummary saveFailedSummary(Long jobId, RepositorySummaryLevel level, String identity,
            String contentIdentity, String cacheKey, String error) {
        RepositoryAnalysisJob job = required(jobId);
        return summaries.save(RepositorySummary.failed(job, job.getUser(), level, identity, contentIdentity, cacheKey, error));
    }

    @Transactional(readOnly = true)
    public Optional<RepositorySummary> cachedSummary(Long userId, String cacheKey) {
        return summaries.findFirstByUserIdAndCacheKeyAndStatusOrderByCreatedAtDesc(userId, cacheKey, "COMPLETED");
    }

    @Transactional(readOnly = true)
    public List<RepositorySummary> summaries(Long jobId) { return summaries.findByJobIdOrderByIdAsc(jobId); }

    @Transactional(readOnly = true)
    public List<RepositorySummaryResponse> summaryResponses(Long jobId) {
        return summaries.findByJobIdOrderByIdAsc(jobId).stream().map(RepositorySummaryResponse::from).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void skipUnit(Long jobId, Long unitId, String reason) {
        RepositoryAnalysisJob job = requiredForUpdate(jobId);
        RepositoryAnalysisUnit unit = units.findById(unitId).orElseThrow();
        if (unit.getStatus() == RepositoryAnalysisUnitStatus.COMPLETED || unit.getStatus() == RepositoryAnalysisUnitStatus.SKIPPED) return;
        unit.skipped(reason); job.unitSkipped();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failUnit(Long unitId, String reason, boolean cancelled) {
        RepositoryAnalysisUnit unit = units.findById(unitId).orElseThrow();
        if (cancelled) unit.cancelled(); else unit.failed(reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepositoryAnalysisJob requestCancel(Long id, Long userId) {
        RepositoryAnalysisJob job = jobs.findLockedByIdAndUserId(id, userId).orElseThrow(() -> new RepositorySnapshotNotFoundException(id));
        if (EnumSet.of(RepositoryAnalysisJobStatus.QUEUED, RepositoryAnalysisJobStatus.RUNNING).contains(job.getStatus())) job.requestCancel();
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelled(Long id) { requiredForUpdate(id).cancelled(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id, boolean partial, String message) { requiredForUpdate(id).complete(partial, message); stage(id, RepositoryAnalysisStageType.FINALIZING).completed(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Long id, RepositoryAnalysisStageType stageType, String message) {
        requiredForUpdate(id).fail(message); stage(id, stageType).failed(message);
    }

    @Transactional(readOnly = true)
    public boolean cancellationRequested(Long id) { return required(id).isCancelRequested(); }

    @Transactional(readOnly = true)
    public List<Long> interruptedCandidateIds() {
        return jobs.findByStatusIn(List.of(RepositoryAnalysisJobStatus.QUEUED, RepositoryAnalysisJobStatus.RUNNING))
                .stream().map(RepositoryAnalysisJob::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> jobIdsForSnapshot(Long snapshotId) {
        return jobs.findBySnapshotId(snapshotId).stream().map(RepositoryAnalysisJob::getId).toList();
    }

    @Transactional
    public void deleteForSnapshot(Long snapshotId) {
        List<Long> ids = jobs.findBySnapshotId(snapshotId).stream().map(RepositoryAnalysisJob::getId).toList();
        if (ids.isEmpty()) return;
        summaries.deleteByJobIdIn(ids); units.deleteByJobIdIn(ids); stages.deleteByJobIdIn(ids); jobs.deleteBySnapshotId(snapshotId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean checkpointForResume(Long id, String snapshotHash, String parserVersion, String promptVersion, String schemaVersion) {
        RepositoryAnalysisJob job = requiredForUpdate(id);
        boolean valid = job.getSnapshotHash().equals(snapshotHash) && job.getParserVersion().equals(parserVersion)
                && job.getPromptVersion().equals(promptVersion) && job.getSchemaVersion().equals(schemaVersion)
                && !units.findByJobIdOrderBySequenceAsc(id).isEmpty() && job.getDeadlineAt().isAfter(Instant.now());
        if (!valid) { job.interrupted("Interrupted repository analysis has no valid resumable checkpoint"); return false; }
        units.findByJobIdOrderBySequenceAsc(id).forEach(RepositoryAnalysisUnit::resetPending);
        job.queuedForResume(); return true;
    }

    private RepositoryAnalysisJob required(Long id) { return jobs.findById(id).orElseThrow(() -> new RepositorySnapshotNotFoundException(id)); }
    private RepositoryAnalysisJob requiredForUpdate(Long id) { return jobs.findLockedById(id).orElseThrow(() -> new RepositorySnapshotNotFoundException(id)); }
    private RepositoryAnalysisStage stage(Long id, RepositoryAnalysisStageType type) { return stages.findByJobIdAndType(id, type).orElseThrow(); }

    public record UnitSpec(String chunkId, String relativePath, String fileHash, int startLine,
                           int endLine, int sequence, int inputTokens, String skipReason) { }
    public record ExecutionContext(User user, Long snapshotId, String snapshotHash, String profileId,
                                   String model, String parserVersion, String promptVersion,
                                   String schemaVersion, Instant deadlineAt, int usedCalls,
                                   int usedInputTokens, int usedOutputTokens,
                                   RepositoryAnalysisJobStatus status) { }
}
