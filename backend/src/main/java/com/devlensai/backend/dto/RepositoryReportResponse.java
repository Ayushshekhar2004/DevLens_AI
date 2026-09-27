package com.devlensai.backend.dto;

import com.devlensai.backend.entity.RepositoryFinding;
import com.devlensai.backend.entity.RepositoryReport;

import java.util.List;
import java.util.Map;
import java.util.Comparator;

public record RepositoryReportResponse(
        Long jobId, String status, String snapshotHash, String provider, String model,
        String promptVersion, String schemaVersion, int eligibleFiles, int reviewedFiles,
        int skippedFiles, int unsupportedFiles, int incompletePasses, boolean partialCoverage,
        String coverageLabel, List<ModuleCoverage> moduleCoverage, BudgetUsage budgetUsage, List<Finding> findings
) {
    public record BudgetUsage(int calls, int inputTokens, int outputTokens) { }
    public record ModuleCoverage(String module, int eligibleFiles, int reviewedFiles, String status) { }
    public record Finding(String id, String category, String severity, String confidence,
            String claim, String rationale, RepositoryEvidenceReference primaryLocation,
            List<RepositoryEvidenceReference> relatedEvidence, String suggestedRemediation,
            String provenance, String snapshotHash, String model) { }

    public static RepositoryReportResponse from(RepositoryReport report) {
        var job = report.getJob();
        return new RepositoryReportResponse(job.getId(), report.getStatus(), job.getSnapshotHash(),
                job.getProviderName(), job.getModelName(), job.getPromptVersion(), job.getSchemaVersion(),
                report.getEligibleFiles(), report.getReviewedFiles(), report.getSkippedFiles(),
                report.getUnsupportedFiles(), report.getIncompletePasses(), report.isPartialCoverage(),
                report.getCoverageLabel(), report.getModuleCoverage().stream().map(value -> new ModuleCoverage(
                        value.module(), value.eligibleFiles(), value.reviewedFiles(), value.status())).toList(),
                new BudgetUsage(report.getUsedCalls(), report.getUsedInputTokens(),
                report.getUsedOutputTokens()), report.getFindings().stream().map(RepositoryReportResponse::finding)
                .sorted(findingOrder()).toList());
    }

    private static Finding finding(RepositoryFinding value) {
        return new Finding(value.getStableId(), value.getCategory(), value.getSeverity(), value.getConfidence(),
                value.getClaim(), value.getRationale(), value.getPrimaryLocation(), value.getRelatedEvidence(),
                value.getSuggestedRemediation(), value.getProvenance(), value.getSnapshotHash(), value.getModelName());
    }
    private static Comparator<Finding> findingOrder() {
        Map<String,Integer> rank = Map.of("CRITICAL", 0, "HIGH", 1, "MEDIUM", 2, "LOW", 3);
        return Comparator.comparingInt((Finding value) -> rank.getOrDefault(value.severity(), 4))
                .thenComparing(value -> value.primaryLocation().relativePath())
                .thenComparingInt(value -> value.primaryLocation().startLine()).thenComparing(Finding::id);
    }
}
