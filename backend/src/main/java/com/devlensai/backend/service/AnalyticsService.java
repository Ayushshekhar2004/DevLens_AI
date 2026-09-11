package com.devlensai.backend.service;

import com.devlensai.backend.dto.AnalyticsOverviewResponse;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.entity.SecuritySeverity;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.repository.AnalysisRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;

@Service
public class AnalyticsService {

    private static final int RECENT_ANALYSIS_LIMIT = 5;

    private final AnalysisRepository analysisRepository;

    public AnalyticsService(AnalysisRepository analysisRepository) {
        this.analysisRepository = analysisRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(User user) {
        Long userId = user.getId();

        EnumMap<ProgrammingLanguage, Long> languageCounts = new EnumMap<>(ProgrammingLanguage.class);
        analysisRepository.countAnalysesByLanguage(userId).forEach(metric ->
                languageCounts.put(metric.getLanguage(), metric.getTotal()));
        long totalAnalyses = languageCounts.values().stream().mapToLong(Long::longValue).sum();

        EnumMap<SecuritySeverity, Long> severityCounts = new EnumMap<>(SecuritySeverity.class);
        analysisRepository.countSecurityFindingsBySeverity(userId).forEach(metric ->
                severityCounts.put(metric.getSeverity(), metric.getTotal()));

        var recentAnalyses = analysisRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT_ANALYSIS_LIMIT))
                .stream()
                .map(analysis -> new AnalyticsOverviewResponse.RecentAnalysis(
                        analysis.getId(),
                        analysis.getProgrammingLanguage(),
                        analysis.getStatus(),
                        analysis.getCreatedAt(),
                        analysis.getSummary()
                ))
                .toList();

        var analysesByLanguage = Arrays.stream(ProgrammingLanguage.values())
                .map(language -> new AnalyticsOverviewResponse.LanguageMetric(
                        language, languageCounts.getOrDefault(language, 0L)))
                .toList();

        var findingsBySeverity = Arrays.stream(SecuritySeverity.values())
                .map(severity -> new AnalyticsOverviewResponse.SecuritySeverityMetric(
                        severity, severityCounts.getOrDefault(severity, 0L)))
                .toList();

        return new AnalyticsOverviewResponse(
                totalAnalyses,
                analysesByLanguage,
                recentAnalyses,
                analysisRepository.countGeneratedTestCasesByUserId(userId),
                findingsBySeverity
        );
    }
}
