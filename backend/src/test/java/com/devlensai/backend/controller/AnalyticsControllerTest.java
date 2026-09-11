package com.devlensai.backend.controller;

import com.devlensai.backend.dto.AnalyticsOverviewResponse;
import com.devlensai.backend.entity.AnalysisStatus;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.entity.SecuritySeverity;
import com.devlensai.backend.repository.UserRepository;
import com.devlensai.backend.service.AnalyticsService;
import com.devlensai.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void returnsCurrentUserOverview() throws Exception {
        when(analyticsService.overview(any())).thenReturn(new AnalyticsOverviewResponse(
                4,
                List.of(new AnalyticsOverviewResponse.LanguageMetric(ProgrammingLanguage.JAVA, 4)),
                List.of(new AnalyticsOverviewResponse.RecentAnalysis(
                        10L, ProgrammingLanguage.JAVA, AnalysisStatus.COMPLETED,
                        Instant.parse("2026-09-11T10:00:00Z"), "Stored summary"
                )),
                15,
                List.of(new AnalyticsOverviewResponse.SecuritySeverityMetric(SecuritySeverity.HIGH, 2))
        ));

        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAnalyses").value(4))
                .andExpect(jsonPath("$.analysesByLanguage[0].language").value("JAVA"))
                .andExpect(jsonPath("$.recentAnalyses[0].id").value(10))
                .andExpect(jsonPath("$.totalGeneratedTestCases").value(15))
                .andExpect(jsonPath("$.securityFindingsBySeverity[0].severity").value("HIGH"));
    }
}
