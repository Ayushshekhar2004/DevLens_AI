package com.devlensai.backend.controller;

import com.devlensai.backend.dto.*;
import com.devlensai.backend.repository.UserRepository;
import com.devlensai.backend.service.JwtService;
import com.devlensai.backend.service.RepositoryAnalysisOrchestrator;
import com.devlensai.backend.service.RepositoryContextRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RepositoryAnalysisController.class)
@AutoConfigureMockMvc(addFilters = false)
class RepositoryAnalysisControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean RepositoryAnalysisOrchestrator orchestrator;
    @MockitoBean RepositoryContextRetrievalService retrieval;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    @Test
    void startsAndPollsBoundedOwnerScopedJob() throws Exception {
        when(orchestrator.start(any(), eq(4L), any())).thenReturn(response("QUEUED"));
        when(orchestrator.status(any(), eq(9L))).thenReturn(response("RUNNING"));
        when(orchestrator.cancel(any(), eq(9L))).thenReturn(response("CANCELLED"));
        when(orchestrator.units(any(), eq(9L), eq(0), eq(50))).thenReturn(new RepositoryInventoryResponse.Page<>(
                List.of(new RepositoryAnalysisUnitResponse("c".repeat(64), "src/Main.java", "f".repeat(64),
                        1, 5, 0, "COMPLETED", 30, 10, "summary", null)), 0, 50, 1, 1, true, true));
        when(orchestrator.summaries(any(), eq(9L))).thenReturn(List.of(new RepositorySummaryResponse(
                "FILE", "src/Main.java", "COMPLETED", "Defines Main", List.of("Main"), List.of(),
                "Bounded evidence only", List.of(new RepositoryEvidenceReference("src/Main.java", 1, 5)), false, null)));
        when(orchestrator.report(any(), eq(9L))).thenReturn(new RepositoryReportResponse(9L, "PARTIAL", "a".repeat(64),
                "ollama", "qwen:latest", "repository-review-v1", "repository-finding-v1", 2, 1, 1, 0, 1,
                true, "Partial advisory review", List.of(new RepositoryReportResponse.ModuleCoverage("main", 2, 1, "PARTIAL")),
                new RepositoryReportResponse.BudgetUsage(3, 100, 20),
                List.of(new RepositoryReportResponse.Finding("f".repeat(64), "VALIDATION", "HIGH", "QUALITATIVE",
                        "Missing validation", "Evidence-backed", new RepositoryEvidenceReference("src/Main.java", 2, 2),
                        List.of(), "Validate input", "AI", "a".repeat(64), "qwen:latest"))));
        when(retrieval.inspect(any(), eq(4L), any())).thenReturn(new RepositoryRetrievalInspectionResponse(
                "trace request", "a".repeat(64), 1000, 20, false, false,
                List.of(new RepositoryRetrievalInspectionResponse.Selected("src/Main.java", "f".repeat(64),
                        1, 5, 20, 1000, List.of("EXACT_PATH"), null)), List.of(), List.of()));

        mockMvc.perform(post("/api/repositories/snapshots/4/analysis-jobs")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"profileId\":\"local\",\"model\":\"qwen:latest\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.model").value("qwen:latest"));
        mockMvc.perform(get("/api/repositories/analysis-jobs/9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"));
        mockMvc.perform(post("/api/repositories/analysis-jobs/9/cancel"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/repositories/analysis-jobs/9/units"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].relativePath").value("src/Main.java"))
                .andExpect(jsonPath("$.content[0].source").doesNotExist());
        mockMvc.perform(get("/api/repositories/analysis-jobs/9/summaries"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].level").value("FILE"))
                .andExpect(jsonPath("$[0].evidence[0].relativePath").value("src/Main.java"));
        mockMvc.perform(get("/api/repositories/analysis-jobs/9/report"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.partialCoverage").value(true))
                .andExpect(jsonPath("$.findings[0].provenance").value("AI"));
        mockMvc.perform(post("/api/repositories/snapshots/4/context-inspection")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"targetType":"FILE","target":"src/Main.java","purpose":"trace request",
                                "snapshotHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selected[0].relativePath").value("src/Main.java"))
                .andExpect(jsonPath("$.selected[0].content").doesNotExist());
        verify(orchestrator).start(any(), eq(4L), any(StartRepositoryAnalysisRequest.class));
    }

    @Test
    void rejectsArbitraryOrMissingProviderSelection() throws Exception {
        mockMvc.perform(post("/api/repositories/snapshots/4/analysis-jobs")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"profileId\":\"http://public.example\",\"model\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private RepositoryAnalysisJobResponse response(String status) {
        Instant now = Instant.parse("2026-09-24T12:00:00Z");
        return new RepositoryAnalysisJobResponse(9L, 4L, status, "VALIDATING", 0, "ollama", "local",
                "qwen:latest", "a".repeat(64), "repository-summary-v1", "lexical-v1", "repository-summary-v1",
                0, 0, 0, new RepositoryAnalysisJobResponse.BudgetUsage(0, 0, 0), null,
                now.plusSeconds(30), now, now, List.of());
    }
}
