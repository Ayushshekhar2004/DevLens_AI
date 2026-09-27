package com.devlensai.backend.service;

import com.devlensai.backend.ai.RepositoryAnalysisProvider;
import com.devlensai.backend.config.RepositoryAnalysisProperties;
import com.devlensai.backend.dto.*;
import com.devlensai.backend.entity.*;
import com.devlensai.backend.exception.RepositoryAnalysisException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class RepositoryReviewService {
    static final String PROVENANCE = "AI";
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private final RepositoryContextRetrievalService retrieval;
    private final RepositoryAnalysisProvider provider;
    private final RepositoryAnalysisProperties properties;
    private final RepositoryAnalysisStateService state;
    private final ExecutorService inference;

    public RepositoryReviewService(RepositoryContextRetrievalService retrieval, RepositoryAnalysisProvider provider,
            RepositoryAnalysisProperties properties, RepositoryAnalysisStateService state,
            @Qualifier("localInferenceExecutor") ExecutorService inference) {
        this.retrieval = retrieval; this.provider = provider; this.properties = properties;
        this.state = state; this.inference = inference;
    }

    public ReviewOutcome review(Long jobId, RepositoryAnalysisStateService.ExecutionContext context,
            RepositoryAnalysisPreparationService.Prepared prepared) throws InterruptedException {
        List<RepositoryFileRecord> eligible = prepared.files().stream()
                .filter(file -> file.safeContent() != null && !file.safeContent().isBlank()).toList();
        int unsupported = prepared.files().size() - eligible.size();
        int reviewed = 0, incomplete = 0;
        Set<String> reviewedPaths = new TreeSet<>();
        List<ValidatedFinding> accepted = new ArrayList<>();
        for (RepositoryFileRecord target : eligible) {
            if (state.cancellationRequested(jobId)) throw new InterruptedException();
            var latest = state.context(jobId);
            if (latest.usedCalls() >= properties.maxCalls() || latest.usedInputTokens() >= properties.maxInputTokens()
                    || latest.usedOutputTokens() >= properties.maxOutputTokens()) { incomplete += eligible.size() - reviewed; break; }
            RepositoryContextPack pack = retrieval.retrieve(context.user(), context.snapshotId(),
                    new RepositoryRetrievalRequest("FILE", target.relativePath(),
                            "Review cross-file validation, caller/callee contracts, and error handling", context.snapshotHash()));
            if (pack.missingContext()) { incomplete++; continue; }
            String payload = payload(pack);
            int inputTokens = estimate(payload);
            if (latest.usedInputTokens() + inputTokens > properties.maxInputTokens()) { incomplete += eligible.size() - reviewed; break; }
            state.consumeSummaryCall(jobId, inputTokens);
            try {
                Future<RepositoryReviewResult> future = inference.submit(() -> provider.reviewRepository(target.relativePath(), payload,
                        allowed(pack), context.profileId(), context.model()));
                RepositoryReviewResult result = future.get(properties.callTimeoutSeconds(), TimeUnit.SECONDS);
                int output = estimate(result.toString());
                if (state.context(jobId).usedOutputTokens() + output > properties.maxOutputTokens()) { incomplete++; continue; }
                state.consumeSummaryOutput(jobId, output); reviewed++; reviewedPaths.add(target.relativePath());
                if (result.insufficientContext()) { incomplete++; continue; }
                for (RepositoryFindingCandidate candidate : result.findings()) {
                    Optional<ValidatedFinding> validated = validateCandidate(candidate, pack);
                    if (validated.isPresent()) accepted.add(validated.get()); else incomplete++;
                }
            } catch (TimeoutException exception) { incomplete++; }
            catch (ExecutionException | RejectedExecutionException exception) { incomplete++; }
        }
        for (RepositoryModuleRecord module : prepared.modules()) {
            if (state.cancellationRequested(jobId)) throw new InterruptedException();
            var latest = state.context(jobId);
            if (latest.usedCalls() >= properties.maxCalls() || latest.usedInputTokens() >= properties.maxInputTokens()
                    || latest.usedOutputTokens() >= properties.maxOutputTokens()) { incomplete++; break; }
            RepositoryContextPack pack = retrieval.retrieve(context.user(), context.snapshotId(),
                    new RepositoryRetrievalRequest("MODULE", module.name(),
                            "Review module boundaries, cross-file contracts, validation, and error handling", context.snapshotHash()));
            if (pack.missingContext()) { incomplete++; continue; }
            String payload = payload(pack); int inputTokens = estimate(payload);
            if (latest.usedInputTokens() + inputTokens > properties.maxInputTokens()) { incomplete++; break; }
            state.consumeSummaryCall(jobId, inputTokens);
            try {
                Future<RepositoryReviewResult> future = inference.submit(() -> provider.reviewRepository("module:" + module.name(),
                        payload, allowed(pack), context.profileId(), context.model()));
                RepositoryReviewResult result = future.get(properties.callTimeoutSeconds(), TimeUnit.SECONDS);
                int output = estimate(result.toString());
                if (state.context(jobId).usedOutputTokens() + output > properties.maxOutputTokens()) { incomplete++; continue; }
                state.consumeSummaryOutput(jobId, output);
                if (result.insufficientContext()) { incomplete++; continue; }
                for (RepositoryFindingCandidate candidate : result.findings()) {
                    Optional<ValidatedFinding> validated = validateCandidate(candidate, pack);
                    if (validated.isPresent()) accepted.add(validated.get()); else incomplete++;
                }
            } catch (TimeoutException | ExecutionException | RejectedExecutionException exception) { incomplete++; }
        }
        List<ValidatedFinding> aggregated = aggregate(accepted);
        int skipped = Math.max(0, eligible.size() - reviewed);
        boolean partial = incomplete > 0 || skipped > 0 || unsupported > 0;
        String label = partial
                ? "Partial advisory review: unreviewed or unsupported areas remain; no-findings is not a clean bill of health"
                : "Completed advisory review of eligible indexed context; no-findings is not a security or correctness certification";
        state.saveReport(jobId, eligible.size(), reviewed, skipped, unsupported, incomplete, partial, label,
                moduleCoverage(prepared, reviewedPaths), aggregated);
        return new ReviewOutcome(partial, aggregated.size());
    }

    public RepositoryReportResponse report(User user, Long jobId) {
        return state.report(jobId, user.getId());
    }

    Optional<ValidatedFinding> validateCandidate(RepositoryFindingCandidate value, RepositoryContextPack pack) {
        if (value == null || !SEVERITIES.contains(value.severity()) || !valid(value.primaryLocation(), pack)) return Optional.empty();
        List<RepositoryEvidenceReference> validRelated = value.relatedEvidence().stream().filter(reference -> valid(reference, pack))
                .distinct().sorted(Comparator.comparing(RepositoryEvidenceReference::relativePath)
                        .thenComparingInt(RepositoryEvidenceReference::startLine).thenComparingInt(RepositoryEvidenceReference::endLine)).toList();
        boolean unsupported = validRelated.size() != value.relatedEvidence().size();
        String category = normalizeCategory(value.category());
        String claim = inert(value.claim());
        if (category.isBlank() || claim.isBlank()) return Optional.empty();
        String severity = unsupported ? downgrade(value.severity()) : value.severity();
        String confidence = inert(value.confidence());
        if (confidence.matches(".*\\b\\d{1,3}(?:\\.\\d+)?%.*")) {
            confidence = "MODEL_UNCERTAINTY: quantitative confidence was discarded; treat this as an advisory hypothesis";
        }
        if (unsupported) confidence = "UNCERTAIN: unsupported related evidence was removed. " + confidence;
        RepositoryEvidenceReference primary = value.primaryLocation();
        String id = hash(category + "|" + primary.relativePath() + "|" + primary.startLine() + "|"
                + primary.endLine() + "|" + normalized(claim));
        return Optional.of(new ValidatedFinding(id, category, severity, confidence, claim, inert(value.rationale()),
                primary, validRelated, inert(value.suggestedRemediation()), PROVENANCE));
    }

    List<ValidatedFinding> aggregate(List<ValidatedFinding> input) {
        List<ValidatedFinding> ordered = input.stream().sorted(order()).toList();
        List<ValidatedFinding> output = new ArrayList<>();
        for (ValidatedFinding candidate : ordered) {
            int duplicate = indexOfDuplicate(output, candidate);
            if (duplicate >= 0) {
                ValidatedFinding previous = output.get(duplicate);
                output.set(duplicate, merge(previous, candidate));
            } else {
                for (int index = 0; index < output.size(); index++) {
                    ValidatedFinding previous = output.get(index);
                    if (sameArea(previous, candidate) && contradictory(previous.claim(), candidate.claim())) {
                        output.set(index, uncertain(previous)); candidate = uncertain(candidate);
                    }
                }
                output.add(candidate);
            }
        }
        return output.stream().sorted(order()).toList();
    }

    private int indexOfDuplicate(List<ValidatedFinding> values, ValidatedFinding candidate) {
        for (int index = 0; index < values.size(); index++) {
            ValidatedFinding value = values.get(index);
            if (sameArea(value, candidate) && !contradictory(value.claim(), candidate.claim())
                    && similarity(value.claim(), candidate.claim()) >= .60) return index;
        }
        return -1;
    }
    private ValidatedFinding merge(ValidatedFinding left, ValidatedFinding right) {
        List<RepositoryEvidenceReference> evidence = new ArrayList<>(left.related()); evidence.addAll(right.related());
        var related = evidence.stream().distinct().sorted(Comparator.comparing(RepositoryEvidenceReference::relativePath)
                .thenComparingInt(RepositoryEvidenceReference::startLine).thenComparingInt(RepositoryEvidenceReference::endLine)).toList();
        RepositoryEvidenceReference primary = left.primary().startLine() <= right.primary().startLine() ? left.primary() : right.primary();
        String claim = normalized(left.claim()).compareTo(normalized(right.claim())) <= 0 ? left.claim() : right.claim();
        String id = hash(left.category() + "|" + primary.relativePath() + "|" + primary.startLine() + "|"
                + primary.endLine() + "|" + normalized(claim));
        return new ValidatedFinding(id, left.category(), higher(left.severity(), right.severity()),
                left.confidence() + " | corroborated overlapping evidence", claim,
                left.rationale().length() >= right.rationale().length() ? left.rationale() : right.rationale(),
                primary, related, left.remediation(), left.provenance());
    }
    private ValidatedFinding uncertain(ValidatedFinding value) {
        if (value.confidence().startsWith("CONFLICTING_EVIDENCE:")) return value;
        return new ValidatedFinding(value.id(), value.category(), value.severity(),
                "CONFLICTING_EVIDENCE: preserve both advisory conclusions. " + value.confidence(), value.claim(),
                value.rationale(), value.primary(), value.related(), value.remediation(), value.provenance());
    }
    private Comparator<ValidatedFinding> order() {
        Map<String,Integer> rank = Map.of("CRITICAL", 0, "HIGH", 1, "MEDIUM", 2, "LOW", 3);
        return Comparator.comparingInt((ValidatedFinding value) -> rank.get(value.severity()))
                .thenComparing(value -> value.primary().relativePath()).thenComparingInt(value -> value.primary().startLine())
                .thenComparing(ValidatedFinding::category).thenComparing(ValidatedFinding::id);
    }
    private boolean valid(RepositoryEvidenceReference value, RepositoryContextPack pack) {
        if (value == null || value.startLine() < 1 || value.endLine() < value.startLine()) return false;
        return pack.snippets().stream().anyMatch(snippet -> snippet.relativePath().equals(value.relativePath())
                && value.startLine() >= snippet.startLine() && value.endLine() <= snippet.endLine());
    }
    private boolean sameArea(ValidatedFinding a, ValidatedFinding b) {
        return a.category().equals(b.category()) && a.primary().relativePath().equals(b.primary().relativePath())
                && a.primary().startLine() <= b.primary().endLine() && b.primary().startLine() <= a.primary().endLine();
    }
    private boolean contradictory(String a, String b) {
        return negative(a) != negative(b) && similarity(a, b) >= .25;
    }
    private boolean negative(String value) { String n = normalized(value); return n.contains(" no ") || n.startsWith("no ") || n.contains(" not ") || n.contains("safe"); }
    private double similarity(String a, String b) {
        Set<String> left = new HashSet<>(List.of(normalized(a).split(" "))), right = new HashSet<>(List.of(normalized(b).split(" ")));
        Set<String> union = new HashSet<>(left); union.addAll(right); left.retainAll(right);
        return union.isEmpty() ? 0 : (double) left.size() / union.size();
    }
    private String payload(RepositoryContextPack pack) {
        StringBuilder value = new StringBuilder("Purpose: ").append(pack.purpose()).append('\n');
        for (var snippet : pack.snippets()) value.append("<evidence path=\"").append(snippet.relativePath())
                .append("\" hash=\"").append(snippet.fileHash()).append("\" lines=\"").append(snippet.startLine())
                .append('-').append(snippet.endLine()).append("\">\n").append(snippet.content()).append("\n</evidence>\n")
                .append(snippet.summaryHint() == null ? "" : "Summary hint: " + snippet.summaryHint() + "\n");
        return value.toString();
    }
    private List<RepositoryEvidenceReference> allowed(RepositoryContextPack pack) { return pack.snippets().stream()
            .map(value -> new RepositoryEvidenceReference(value.relativePath(), value.startLine(), value.endLine())).toList(); }
    private int estimate(String value) { return Math.max(1, (value.length() + 2) / 3 + 16); }
    private String normalizeCategory(String value) {
        String normalized = (value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        return normalized.substring(0, Math.min(64, normalized.length()));
    }
    private String normalized(String value) { return (" " + (value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim()) + " "); }
    private String inert(String value) { String safe = value == null ? "" : value; return safe.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replaceAll("(?i)javascript\\s*:", "blocked:"); }
    private String downgrade(String value) { return switch (value) { case "CRITICAL" -> "HIGH"; case "HIGH" -> "MEDIUM"; case "MEDIUM" -> "LOW"; default -> "LOW"; }; }
    private String higher(String a, String b) { List<String> order = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW"); return order.indexOf(a) <= order.indexOf(b) ? a : b; }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }

    private List<RepositoryModuleCoverage> moduleCoverage(RepositoryAnalysisPreparationService.Prepared prepared,
            Set<String> reviewedPaths) {
        List<RepositoryModuleCoverage> result = new ArrayList<>();
        for (RepositoryModuleRecord module : prepared.modules().stream().sorted(Comparator.comparing(RepositoryModuleRecord::name)).toList()) {
            List<RepositoryFileRecord> files = prepared.files().stream().filter(file -> inModule(file.relativePath(), module.rootPath())
                    && file.safeContent() != null && !file.safeContent().isBlank()).toList();
            int reviewed = (int) files.stream().filter(file -> reviewedPaths.contains(file.relativePath())).count();
            String status = reviewed == files.size() ? "COMPLETE" : reviewed == 0 ? "SKIPPED" : "PARTIAL";
            result.add(new RepositoryModuleCoverage(module.name(), files.size(), reviewed, status));
        }
        return List.copyOf(result);
    }
    private boolean inModule(String path, String root) { return root == null || root.isBlank() || path.equals(root) || path.startsWith(root + "/"); }

    record ValidatedFinding(String id, String category, String severity, String confidence, String claim,
            String rationale, RepositoryEvidenceReference primary, List<RepositoryEvidenceReference> related,
            String remediation, String provenance) { }
    public record ReviewOutcome(boolean partial, int findings) { }
}
