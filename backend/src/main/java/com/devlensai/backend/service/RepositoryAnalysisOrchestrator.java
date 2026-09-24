package com.devlensai.backend.service;

import com.devlensai.backend.ai.RepositoryAnalysisProvider;
import com.devlensai.backend.config.RepositoryAnalysisProperties;
import com.devlensai.backend.dto.RepositoryAnalysisJobResponse;
import com.devlensai.backend.dto.RepositoryAnalysisUnitResponse;
import com.devlensai.backend.dto.RepositorySummaryResponse;
import com.devlensai.backend.dto.RepositoryInventoryResponse;
import com.devlensai.backend.dto.StartRepositoryAnalysisRequest;
import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.*;
import com.devlensai.backend.repository.RepositoryAnalysisUnitRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class RepositoryAnalysisOrchestrator {
    static final String PROMPT_VERSION = "repository-summary-v1";
    static final String SCHEMA_VERSION = "repository-summary-v1";
    private static final int MAX_PAGE_SIZE = 100;
    private final RepositoryAnalysisProvider provider;
    private final RepositoryAnalysisProperties properties;
    private final RepositoryAnalysisPreparationService preparation;
    private final RepositoryAnalysisStateService state;
    private final RepositoryChunker chunker;
    private final RepositoryHierarchySummaryService hierarchy;
    private final RepositoryAnalysisUnitRepository unitRepository;
    private final ThreadPoolTaskExecutor executor;
    private final Map<Long, Future<?>> running = new ConcurrentHashMap<>();

    public RepositoryAnalysisOrchestrator(RepositoryAnalysisProvider provider,
            RepositoryAnalysisProperties properties, RepositoryAnalysisPreparationService preparation,
            RepositoryAnalysisStateService state, RepositoryChunker chunker,
            RepositoryHierarchySummaryService hierarchy,
            RepositoryAnalysisUnitRepository unitRepository,
            @Qualifier("repositoryAnalysisExecutor") ThreadPoolTaskExecutor executor) {
        this.provider = provider; this.properties = properties; this.preparation = preparation;
        this.state = state; this.chunker = chunker; this.hierarchy = hierarchy; this.unitRepository = unitRepository;
        this.executor = executor;
    }

    public synchronized RepositoryAnalysisJobResponse start(User user, Long snapshotId, StartRepositoryAnalysisRequest request) {
        RepositoryAnalysisPreparationService.Prepared prepared = preparation.prepare(user, snapshotId);
        provider.validateSelection(request.profileId(), request.model());
        Optional<RepositoryAnalysisJob> duplicate = state.idempotent(snapshotId, request.profileId(), request.model(), prepared.snapshotHash());
        if (duplicate.isPresent()) return state.response(duplicate.get().getId(), user.getId());
        RepositoryAnalysisJob job = state.create(user, prepared.snapshot(), prepared.snapshotHash(), provider.providerName(),
                request.profileId(), request.model(), PROMPT_VERSION, prepared.parserVersion(), SCHEMA_VERSION,
                Instant.now().plusSeconds(properties.jobDeadlineSeconds()));
        submit(job.getId(), true);
        return state.response(job.getId(), user.getId());
    }

    public RepositoryAnalysisJobResponse status(User user, Long jobId) { return state.response(jobId, user.getId()); }

    public RepositoryInventoryResponse.Page<RepositoryAnalysisUnitResponse> units(User user, Long jobId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new RepositoryAnalysisException("Page must be non-negative and size must be between 1 and 100");
        state.owned(jobId, user.getId());
        var result = unitRepository.findByJobId(jobId, PageRequest.of(page, size, Sort.by("sequence")));
        return new RepositoryInventoryResponse.Page<>(result.getContent().stream().map(RepositoryAnalysisUnitResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    public List<RepositorySummaryResponse> summaries(User user, Long jobId) {
        state.owned(jobId, user.getId());
        return state.summaryResponses(jobId);
    }

    public RepositoryAnalysisJobResponse cancel(User user, Long jobId) {
        RepositoryAnalysisJob job = state.requestCancel(jobId, user.getId());
        Future<?> future = running.get(jobId);
        if (future != null) future.cancel(true);
        return state.response(jobId, user.getId());
    }

    public void deleteForSnapshot(Long snapshotId) {
        state.jobIdsForSnapshot(snapshotId).forEach(id -> {
            Future<?> future = running.remove(id);
            if (future != null) future.cancel(true);
        });
        state.deleteForSnapshot(snapshotId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for (Long id : state.interruptedCandidateIds()) {
            try {
                var context = state.context(id);
                var prepared = preparation.prepare(context.user(), context.snapshotId());
                if (state.checkpointForResume(id, prepared.snapshotHash(), prepared.parserVersion(), PROMPT_VERSION, SCHEMA_VERSION)) submit(id, false);
            } catch (RuntimeException exception) {
                state.failed(id, RepositoryAnalysisStageType.VALIDATING, "Interrupted repository analysis could not be recovered safely");
            }
        }
    }

    private void submit(Long jobId, boolean rejectToCaller) {
        try {
            Future<?> future = executor.submit(() -> execute(jobId));
            running.put(jobId, future);
        } catch (RejectedExecutionException exception) {
            state.failed(jobId, RepositoryAnalysisStageType.VALIDATING, "Local repository analysis queue is full; retry later");
            if (rejectToCaller) throw new RepositoryAnalysisQueueFullException();
        }
    }

    private void execute(Long jobId) {
        RepositoryAnalysisStageType activeStage = RepositoryAnalysisStageType.VALIDATING;
        try {
            var context = state.context(jobId);
            state.stageRunning(jobId, activeStage);
            ensureActive(jobId, context.deadlineAt());
            var prepared = preparation.prepare(context.user(), context.snapshotId());
            if (!prepared.snapshotHash().equals(context.snapshotHash()) || !prepared.parserVersion().equals(context.parserVersion())) {
                throw new RepositoryAnalysisException("Repository snapshot or parser checkpoint changed");
            }
            provider.validateSelection(context.profileId(), context.model());
            state.stageCompleted(jobId, activeStage);

            activeStage = RepositoryAnalysisStageType.CHUNKING;
            state.stageRunning(jobId, activeStage);
            List<RepositoryAnalysisUnit> persisted = state.units(jobId);
            Plan plan = plan(prepared.files());
            if (persisted.isEmpty()) state.createUnits(jobId, plan.specs());
            state.stageCompleted(jobId, activeStage);

            activeStage = RepositoryAnalysisStageType.ANALYZING;
            state.stageRunning(jobId, activeStage); state.stageCompleted(jobId, activeStage);

            activeStage = RepositoryAnalysisStageType.SUMMARIZING;
            state.stageRunning(jobId, activeStage);
            boolean budgetTruncated = hierarchy.summarize(jobId, context, prepared, plan.chunksById(),
                    () -> state.cancellationRequested(jobId)).partial();
            state.stageCompleted(jobId, activeStage);

            activeStage = RepositoryAnalysisStageType.FINALIZING;
            state.stageRunning(jobId, activeStage);
            boolean skipped = state.units(jobId).stream().anyMatch(unit -> unit.getStatus() == RepositoryAnalysisUnitStatus.SKIPPED);
            state.complete(jobId, budgetTruncated || skipped,
                    budgetTruncated ? "Repository analysis completed partially because a configured budget was reached" : null);
        } catch (CancellationException | InterruptedException exception) {
            Thread.currentThread().interrupt(); state.cancelled(jobId);
        } catch (TimeoutException exception) {
            state.failed(jobId, activeStage, "Local model call timed out");
        } catch (RuntimeException | ExecutionException exception) {
            state.failed(jobId, activeStage, safeFailure(exception));
        } finally {
            running.remove(jobId);
        }
    }

    private Plan plan(List<RepositoryFileRecord> files) {
        List<RepositoryAnalysisStateService.UnitSpec> specs = new ArrayList<>();
        Map<String, RepositoryChunker.Chunk> chunks = new HashMap<>();
        int sequence = 0;
        int analyzedFiles = 0;
        int plannedCalls = 0;
        for (RepositoryFileRecord file : files) {
            List<RepositoryChunker.Chunk> fileChunks = chunker.chunks(file);
            if (fileChunks.isEmpty()) {
                specs.add(skipSpec(file, sequence++, "UNSUPPORTED_LANGUAGE_OR_NO_SAFE_CONTEXT"));
                continue;
            }
            analyzedFiles++;
            if (analyzedFiles > properties.maxFiles()) {
                specs.add(skipSpec(file, sequence++, "FILE_BUDGET_EXCEEDED"));
                continue;
            }
            int accepted = Math.min(fileChunks.size(), Math.max(0, properties.maxCalls() - plannedCalls));
            for (int index = 0; index < accepted; index++) {
                var chunk = fileChunks.get(index); chunks.put(chunk.id(), chunk);
                specs.add(new RepositoryAnalysisStateService.UnitSpec(chunk.id(), chunk.relativePath(), chunk.fileHash(),
                        chunk.startLine(), chunk.endLine(), sequence++, chunk.estimatedTokens(), null));
            }
            plannedCalls += accepted;
            if (accepted < fileChunks.size()) {
                var first = fileChunks.get(accepted); var last = fileChunks.get(fileChunks.size() - 1);
                specs.add(new RepositoryAnalysisStateService.UnitSpec(hash(file.relativePath() + ":budget:" + first.startLine()),
                        file.relativePath(), file.contentHash(), first.startLine(), last.endLine(), sequence++, 0,
                        "CALL_BUDGET_EXCEEDED"));
            }
        }
        return new Plan(List.copyOf(specs), Map.copyOf(chunks));
    }

    private RepositoryAnalysisStateService.UnitSpec skipSpec(RepositoryFileRecord file, int sequence, String reason) {
        return new RepositoryAnalysisStateService.UnitSpec(hash(file.relativePath() + ":skip:" + reason), file.relativePath(),
                file.contentHash(), 1, Math.max(1, file.lineCount()), sequence, 0, reason);
    }

    private void ensureActive(Long jobId, Instant deadline) throws InterruptedException, TimeoutException {
        if (Thread.currentThread().isInterrupted() || state.cancellationRequested(jobId)) throw new InterruptedException();
        if (!deadline.isAfter(Instant.now())) throw new TimeoutException("Repository analysis deadline exceeded");
    }

    private String safeFailure(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null ? exception.getCause() : exception;
        if (cause instanceof OllamaSelectionException) return cause.getMessage();
        if (cause instanceof AiProviderTimeoutException) return "Local model call timed out";
        if (cause instanceof AiProviderUnavailableException) return "Local Ollama service is unavailable";
        if (cause instanceof AiProviderMalformedResponseException) return "Local model returned invalid structured output";
        if (cause instanceof RepositoryAnalysisException) return cause.getMessage();
        return "Repository analysis failed safely";
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private record Plan(List<RepositoryAnalysisStateService.UnitSpec> specs,
                        Map<String, RepositoryChunker.Chunk> chunksById) { }
}
