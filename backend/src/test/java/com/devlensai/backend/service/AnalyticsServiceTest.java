package com.devlensai.backend.service;

import com.devlensai.backend.entity.Analysis;
import com.devlensai.backend.entity.AnalysisStatus;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.entity.SecuritySeverity;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.repository.AnalysisRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    @Test
    void buildsOverviewOnlyFromOwnerScopedStoredData() {
        AnalysisRepository repository = mock(AnalysisRepository.class);
        AnalyticsService service = new AnalyticsService(repository);
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);

        AnalysisRepository.LanguageCount javaCount = mock(AnalysisRepository.LanguageCount.class);
        when(javaCount.getLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(javaCount.getTotal()).thenReturn(3L);
        AnalysisRepository.SecuritySeverityCount highCount =
                mock(AnalysisRepository.SecuritySeverityCount.class);
        when(highCount.getSeverity()).thenReturn(SecuritySeverity.HIGH);
        when(highCount.getTotal()).thenReturn(2L);

        Analysis recent = mock(Analysis.class);
        Instant createdAt = Instant.parse("2026-09-11T10:00:00Z");
        when(recent.getId()).thenReturn(9L);
        when(recent.getProgrammingLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(recent.getStatus()).thenReturn(AnalysisStatus.COMPLETED);
        when(recent.getCreatedAt()).thenReturn(createdAt);
        when(recent.getSummary()).thenReturn("Stored summary");

        when(repository.countAnalysesByLanguage(42L)).thenReturn(List.of(javaCount));
        when(repository.countSecurityFindingsBySeverity(42L)).thenReturn(List.of(highCount));
        when(repository.countGeneratedTestCasesByUserId(42L)).thenReturn(12L);
        when(repository.findByUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(recent));

        var overview = service.overview(user);

        assertThat(overview.totalAnalyses()).isEqualTo(3);
        assertThat(overview.totalGeneratedTestCases()).isEqualTo(12);
        assertThat(overview.analysesByLanguage())
                .filteredOn(metric -> metric.language() == ProgrammingLanguage.JAVA)
                .singleElement().extracting(metric -> metric.count()).isEqualTo(3L);
        assertThat(overview.analysesByLanguage())
                .filteredOn(metric -> metric.language() == ProgrammingLanguage.PYTHON)
                .singleElement().extracting(metric -> metric.count()).isEqualTo(0L);
        assertThat(overview.securityFindingsBySeverity())
                .filteredOn(metric -> metric.severity() == SecuritySeverity.HIGH)
                .singleElement().extracting(metric -> metric.count()).isEqualTo(2L);
        assertThat(overview.recentAnalyses()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(9L);
            assertThat(item.summary()).isEqualTo("Stored summary");
            assertThat(item.createdAt()).isEqualTo(createdAt);
        });

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(42L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }
}
