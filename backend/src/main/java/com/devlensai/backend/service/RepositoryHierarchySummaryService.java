package com.devlensai.backend.service;

import com.devlensai.backend.ai.RepositoryAnalysisProvider;
import com.devlensai.backend.config.RepositoryAnalysisProperties;
import com.devlensai.backend.dto.RepositoryEvidenceReference;
import com.devlensai.backend.dto.RepositorySummaryResult;
import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.AiProviderMalformedResponseException;
import com.devlensai.backend.exception.RepositoryAnalysisException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

@Service
public class RepositoryHierarchySummaryService {
    static final String PROMPT_VERSION = "repository-summary-v1";
    static final String SCHEMA_VERSION = "repository-summary-v1";
    private final RepositoryAnalysisProvider provider;
    private final RepositoryAnalysisProperties properties;
    private final RepositoryAnalysisStateService state;
    private final ExecutorService inferenceExecutor;

    public RepositoryHierarchySummaryService(RepositoryAnalysisProvider provider, RepositoryAnalysisProperties properties,
            RepositoryAnalysisStateService state, @Qualifier("localInferenceExecutor") ExecutorService inferenceExecutor) {
        this.provider = provider; this.properties = properties; this.state = state; this.inferenceExecutor = inferenceExecutor;
    }

    public Outcome summarize(Long jobId, RepositoryAnalysisStateService.ExecutionContext context,
            RepositoryAnalysisPreparationService.Prepared prepared, Map<String, RepositoryChunker.Chunk> chunks,
            BooleanSupplier cancelled) throws InterruptedException, TimeoutException, ExecutionException {
        boolean partial = false;
        Map<String, List<RepositorySummary>> byFile = new LinkedHashMap<>();
        for (RepositoryAnalysisUnit unit : state.units(jobId)) {
            if (unit.getStatus() != RepositoryAnalysisUnitStatus.PENDING) continue;
            checkActive(context.deadlineAt(), cancelled);
            RepositoryChunker.Chunk chunk = chunks.get(unit.getChunkId());
            if (chunk == null) { state.skipUnit(jobId, unit.getId(), "CHECKPOINT_CHUNK_UNAVAILABLE"); partial = true; continue; }
            int input = chunk.estimatedTokens();
            if (!hasBudget(jobId, input)) { state.skipUnit(jobId, unit.getId(), "CONFIGURED_ANALYSIS_BUDGET_EXCEEDED"); partial = true; continue; }
            state.beginUnit(jobId, unit.getId(), input);
            var allowed = List.of(new RepositoryEvidenceReference(chunk.relativePath(), chunk.startLine(), chunk.endLine()));
            try {
                RepositorySummary summary = obtain(jobId, context, prepared, RepositorySummaryLevel.CHUNK,
                        chunk.id(), chunk.id(), chunk.content(), allowed, false, cancelled);
                int output = estimate(summary);
                state.completeUnit(jobId, unit.getId(), summary.getResponsibilities(), output);
                byFile.computeIfAbsent(chunk.relativePath(), ignored -> new ArrayList<>()).add(summary);
            } catch (RuntimeException exception) {
                state.failUnit(unit.getId(), safe(exception), false); partial = true;
            }
        }

        Map<String, RepositorySummary> files = new LinkedHashMap<>();
        for (var entry : byFile.entrySet()) {
            checkActive(context.deadlineAt(), cancelled);
            String deps = dependenciesFor(entry.getKey(), prepared.dependencyEdges());
            String identity = hash(entry.getValue().stream().map(RepositorySummary::getContentIdentity).toList() + "|" + deps);
            RepositorySummary value = reduce(jobId, context, prepared, RepositorySummaryLevel.FILE, entry.getKey(), identity,
                    entry.getValue(), deps, cancelled);
            if (value != null) files.put(entry.getKey(), value); else partial = true;
        }

        Map<String, List<RepositorySummary>> moduleChildren = new TreeMap<>();
        files.forEach((path, summary) -> moduleChildren.computeIfAbsent(moduleFor(path, prepared), ignored -> new ArrayList<>()).add(summary));
        List<RepositorySummary> modules = new ArrayList<>();
        for (var entry : moduleChildren.entrySet()) {
            String deps = moduleDependencies(entry.getKey(), prepared.dependencyEdges());
            String identity = hash(entry.getValue().stream().map(RepositorySummary::getContentIdentity).toList() + "|" + deps);
            RepositorySummary value = reduce(jobId, context, prepared, RepositorySummaryLevel.MODULE, entry.getKey(), identity,
                    entry.getValue(), deps, cancelled);
            if (value != null) modules.add(value); else partial = true;
        }
        if (!modules.isEmpty()) {
            String identity = hash(modules.stream().map(RepositorySummary::getContentIdentity).toList().toString());
            if (reduce(jobId, context, prepared, RepositorySummaryLevel.REPOSITORY, "repository", identity,
                    modules, "", cancelled) == null) partial = true;
        } else partial = true;
        return new Outcome(partial);
    }

    private RepositorySummary reduce(Long jobId, RepositoryAnalysisStateService.ExecutionContext context,
            RepositoryAnalysisPreparationService.Prepared prepared, RepositorySummaryLevel level, String identity,
            String contentIdentity, List<RepositorySummary> children, String dependencies, BooleanSupplier cancelled)
            throws InterruptedException, TimeoutException, ExecutionException {
        List<RepositorySummary> current = List.copyOf(children); int round = 0;
        while (current.size() > 1 || round == 0) {
            List<RepositorySummary> next = new ArrayList<>(); int batch = 0;
            for (List<RepositorySummary> group : batches(current)) {
                String payload = childPayload(group, dependencies);
                List<RepositoryEvidenceReference> evidence = group.stream().flatMap(v -> v.getEvidence().stream()).distinct().limit(100).toList();
                String batchIdentity = hash(contentIdentity + "|" + round + "|" + batch + "|" + group.stream().map(RepositorySummary::getCacheKey).toList());
                RepositorySummary summary = obtain(jobId, context, prepared, level,
                        identity + (current.size() > 1 ? "#reduce-" + round + "-" + batch : ""),
                        batchIdentity, payload, evidence, true, cancelled);
                if (summary == null || !"COMPLETED".equals(summary.getStatus())) return null;
                next.add(summary); batch++;
            }
            current = next; round++;
            if (current.size() == 1) return current.getFirst();
        }
        return current.isEmpty() ? null : current.getFirst();
    }

    private RepositorySummary obtain(Long jobId, RepositoryAnalysisStateService.ExecutionContext context,
            RepositoryAnalysisPreparationService.Prepared prepared, RepositorySummaryLevel level, String identity,
            String contentIdentity, String untrusted, List<RepositoryEvidenceReference> allowed, boolean countCall,
            BooleanSupplier cancelled) throws InterruptedException, TimeoutException, ExecutionException {
        String cacheKey = cacheIdentity(context.user().getId(), contentIdentity, context.model(),
                context.promptVersion(), context.schemaVersion(), context.parserVersion(), configIdentity());
        var cached = state.cachedSummary(context.user().getId(), cacheKey);
        if (cached.isPresent()) return state.saveSummary(jobId, level, identity, contentIdentity, cacheKey,
                toResult(cached.get()), true);
        int tokens = estimateInput(untrusted);
        if (countCall) {
            if (!hasBudget(jobId, tokens)) {
                return state.saveFailedSummary(jobId, level, identity, contentIdentity, cacheKey, "CONFIGURED_ANALYSIS_BUDGET_EXCEEDED");
            }
            state.consumeSummaryCall(jobId, tokens);
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            checkActive(context.deadlineAt(), cancelled);
            Future<RepositorySummaryResult> future = inferenceExecutor.submit(() -> provider.summarize(level.name(), identity, untrusted, allowed,
                    context.profileId(), context.model()));
            try {
                RepositorySummaryResult result = future.get(properties.callTimeoutSeconds(), TimeUnit.SECONDS);
                validate(result, allowed, prepared.files());
                int output = estimate(result); if (countCall) state.consumeSummaryOutput(jobId, output);
                return state.saveSummary(jobId, level, identity, contentIdentity, cacheKey, result, false);
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof AiProviderMalformedResponseException)) throw exception;
                if (attempt == 1) throw new RepositoryAnalysisException("Local model returned invalid structured summary");
                if (!hasBudget(jobId, tokens)) throw exception;
                state.consumeSummaryCall(jobId, tokens);
            } catch (TimeoutException | InterruptedException exception) { future.cancel(true); throw exception; }
            catch (RepositoryAnalysisException exception) {
                if (attempt == 1 || !hasBudget(jobId, tokens)) throw exception;
                state.consumeSummaryCall(jobId, tokens);
            }
        }
        throw new RepositoryAnalysisException("Local model returned invalid structured summary");
    }

    private void validate(RepositorySummaryResult value, List<RepositoryEvidenceReference> allowed, List<RepositoryFileRecord> files) {
        if (value == null || value.responsibilities() == null || value.keySymbols() == null || value.dependencies() == null
                || value.uncertainty() == null || value.evidence() == null || value.keySymbols().size() > 100
                || value.dependencies().size() > 100 || value.evidence().size() > 100
                || value.toString().length() > properties.outputReserveTokens() * 4
                || value.responsibilities().length() > 16_000 || value.uncertainty().length() > 4_000
                || value.keySymbols().stream().anyMatch(v -> v == null || v.length() > 512)
                || value.dependencies().stream().anyMatch(v -> v == null || v.length() > 512)) {
            throw new RepositoryAnalysisException("Invalid structured summary");
        }
        Map<String,Integer> lines = new HashMap<>(); files.forEach(file -> lines.put(file.relativePath(), file.lineCount()));
        for (RepositoryEvidenceReference reference : value.evidence()) {
            Integer max = lines.get(reference.relativePath());
            boolean permitted = allowed.stream().anyMatch(a -> a.relativePath().equals(reference.relativePath())
                    && reference.startLine() >= a.startLine() && reference.endLine() <= a.endLine());
            if (max == null || reference.startLine() < 1 || reference.endLine() < reference.startLine()
                    || reference.endLine() > max || !permitted) throw new RepositoryAnalysisException("Summary cited evidence outside the exact snapshot");
        }
        if (value.evidence().isEmpty() && value.uncertainty().isBlank()) throw new RepositoryAnalysisException("Summary without evidence must retain uncertainty");
    }

    private List<List<RepositorySummary>> batches(List<RepositorySummary> values) {
        int limit = Math.max(512, properties.usableInputTokens() * 3 / 2); List<List<RepositorySummary>> result = new ArrayList<>();
        List<RepositorySummary> current = new ArrayList<>(); int size = 0;
        for (RepositorySummary value : values) {
            int item = serialize(value).length();
            if (!current.isEmpty() && size + item > limit) { result.add(List.copyOf(current)); current.clear(); size = 0; }
            current.add(value); size += item;
        }
        if (!current.isEmpty()) result.add(List.copyOf(current)); return result;
    }
    private String childPayload(List<RepositorySummary> values, String deps) {
        StringBuilder result = new StringBuilder("Declared dependency metadata: ").append(deps).append('\n');
        values.forEach(value -> result.append("<untrusted_child_summary identity=\"").append(value.getIdentity()).append("\">\n")
                .append(serialize(value)).append("\n</untrusted_child_summary>\n"));
        int maxChars = Math.max(384, (properties.usableInputTokens() - 64) * 3);
        return result.substring(0, Math.min(maxChars, result.length()));
    }
    private String serialize(RepositorySummary value) { return "Responsibilities: " + value.getResponsibilities() + "\nSymbols: " + value.getKeySymbols()
            + "\nDependencies: " + value.getDependencies() + "\nUncertainty: " + value.getUncertainty() + "\nEvidence: " + value.getEvidence(); }
    private boolean hasBudget(Long jobId, int input) { var current = state.context(jobId); return current.usedCalls() < properties.maxCalls()
            && current.usedInputTokens() + input <= properties.maxInputTokens()
            && current.usedOutputTokens() + properties.outputReserveTokens() <= properties.maxOutputTokens(); }
    private int estimateInput(String value) { return Math.max(1, (value.length() + 2) / 3 + 32); }
    private int estimate(RepositorySummary summary) { return estimate(toResult(summary)); }
    private int estimate(RepositorySummaryResult value) { return Math.min(properties.outputReserveTokens(), Math.max(1, value.toString().length() / 3)); }
    private String configIdentity() { return properties.contextTokens() + ":" + properties.systemReserveTokens() + ":"
            + properties.schemaReserveTokens() + ":" + properties.outputReserveTokens() + ":"
            + properties.safetyReserveTokens() + ":" + properties.maxCalls() + ":" + properties.maxInputTokens()
            + ":" + properties.maxOutputTokens() + ":" + properties.maxFiles() + ":" + properties.overlapLines(); }
    static String cacheIdentity(Long userId, String contentIdentity, String model, String promptVersion,
            String schemaVersion, String parserVersion, String configIdentity) {
        return sha(userId + "|" + contentIdentity + "|" + model + "|" + promptVersion + "|" + schemaVersion
                + "|" + parserVersion + "|" + configIdentity);
    }
    private RepositorySummaryResult toResult(RepositorySummary value) { return new RepositorySummaryResult(value.getResponsibilities(), value.getKeySymbols(), value.getDependencies(), value.getUncertainty(), value.getEvidence()); }
    private String dependenciesFor(String path, List<RepositoryDependencyEdgeRecord> deps) { return deps.stream().filter(v -> v.fromPath().equals(path)).sorted(Comparator.comparing(RepositoryDependencyEdgeRecord::target)).map(v -> v.kind() + ":" + v.target() + ":" + v.resolutionStatus()).toList().toString(); }
    private String moduleDependencies(String module, List<RepositoryDependencyEdgeRecord> deps) { return deps.stream().filter(v -> v.fromPath().startsWith(module.isBlank() ? "" : module + "/")).sorted(Comparator.comparing(RepositoryDependencyEdgeRecord::fromPath).thenComparing(RepositoryDependencyEdgeRecord::target)).toList().toString(); }
    private String moduleFor(String path, RepositoryAnalysisPreparationService.Prepared prepared) { return prepared.modules().stream().map(RepositoryModuleRecord::rootPath).filter(root -> root.isBlank() || path.equals(root) || path.startsWith(root + "/")).max(Comparator.comparingInt(String::length)).orElseGet(() -> { int slash = path.indexOf('/'); return slash < 0 ? "root" : path.substring(0, slash); }); }
    private void checkActive(Instant deadline, BooleanSupplier cancelled) throws InterruptedException, TimeoutException { if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new InterruptedException(); if (!deadline.isAfter(Instant.now())) throw new TimeoutException(); }
    private String safe(RuntimeException exception) { return exception instanceof RepositoryAnalysisException ? exception.getMessage() : "Local structured summary failed"; }
    private String hash(String value) { return sha(value); }
    private static String sha(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    public record Outcome(boolean partial) { }
}
