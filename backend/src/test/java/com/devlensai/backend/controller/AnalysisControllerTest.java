package com.devlensai.backend.controller;

import com.devlensai.backend.dto.AnalysisResponse;
import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.dto.CreateAnalysisRequest;
import com.devlensai.backend.dto.GeneratedTestCaseResult;
import com.devlensai.backend.entity.AnalysisStatus;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.entity.TestCaseCategory;
import com.devlensai.backend.exception.AnalysisNotFoundException;
import com.devlensai.backend.exception.AnalysisReviewFailedException;
import com.devlensai.backend.service.AnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-09T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @Test
    void createsAnalysis() throws Exception {
        when(analysisService.create(any(CreateAnalysisRequest.class)))
                .thenReturn(response(1L, ProgrammingLanguage.JAVA));

        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"JAVA","sourceCode":"public class Main {}"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/analyses/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.language").value("JAVA"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.summary").value("Review summary"))
                .andExpect(jsonPath("$.result.generatedTestCases[0].category").value("NORMAL"));
    }

    @Test
    void rejectsBlankSourceCode() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"JAVA","sourceCode":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sourceCode")
                        .value("sourceCode must not be blank"));
    }

    @Test
    void rejectsUnsupportedLanguage() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"RUST","sourceCode":"fn main() {}"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("unsupported")));
    }

    @Test
    void returnsClearGatewayTimeoutWhenAiReviewFails() throws Exception {
        when(analysisService.create(any(CreateAnalysisRequest.class)))
                .thenThrow(new AnalysisReviewFailedException(
                        7L,
                        HttpStatus.GATEWAY_TIMEOUT,
                        "AI provider request timed out",
                        new RuntimeException("provider failure")
                ));

        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"JAVA","sourceCode":"public class Main {}"}
                                """))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message")
                        .value("Analysis 7 was saved as FAILED: AI provider request timed out"));
    }

    @Test
    void returnsAnalysisById() throws Exception {
        when(analysisService.findById(1L)).thenReturn(response(1L, ProgrammingLanguage.JAVA));

        mockMvc.perform(get("/api/analyses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void returnsNotFoundForMissingAnalysis() throws Exception {
        when(analysisService.findById(99L)).thenThrow(new AnalysisNotFoundException(99L));

        mockMvc.perform(get("/api/analyses/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Analysis with id 99 was not found"));
    }

    @Test
    void listsAnalyses() throws Exception {
        when(analysisService.findAllNewestFirst()).thenReturn(List.of(
                response(2L, ProgrammingLanguage.PYTHON),
                response(1L, ProgrammingLanguage.JAVA)
        ));

        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].id").value(1));
    }

    private AnalysisResponse response(Long id, ProgrammingLanguage language) {
        return new AnalysisResponse(
                id,
                language,
                "source code",
                AnalysisStatus.COMPLETED,
                CREATED_AT,
                new CodeReviewResult(
                        "Review summary",
                        List.of(),
                        "O(1)",
                        "O(1)",
                        List.of(),
                        List.of("Keep it readable"),
                        "source code",
                        List.of(new GeneratedTestCaseResult(
                                "Normal input",
                                TestCaseCategory.NORMAL,
                                "source code",
                                "Expected result",
                                "Covers normal behavior",
                                "High confidence"
                        ))
                ),
                null
        );
    }
}
