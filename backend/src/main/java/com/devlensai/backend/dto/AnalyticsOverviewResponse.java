package com.devlensai.backend.dto;

import com.devlensai.backend.entity.AnalysisStatus;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.entity.SecuritySeverity;

import java.time.Instant;
import java.util.List;

public record AnalyticsOverviewResponse(
        long totalAnalyses,
        List<LanguageMetric> analysesByLanguage,
        List<RecentAnalysis> recentAnalyses,
        long totalGeneratedTestCases,
        List<SecuritySeverityMetric> securityFindingsBySeverity
) {
    public record LanguageMetric(ProgrammingLanguage language, long count) {
    }

    public record RecentAnalysis(
            Long id,
            ProgrammingLanguage language,
            AnalysisStatus status,
            Instant createdAt,
            String summary
    ) {
    }

    public record SecuritySeverityMetric(SecuritySeverity severity, long count) {
    }
}
