package com.devlensai.backend.service;

import com.devlensai.backend.dto.AnalysisResponse;
import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.dto.CreateAnalysisRequest;
import com.devlensai.backend.entity.Analysis;
import com.devlensai.backend.entity.AnalysisStatus;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.exception.AiProviderMalformedResponseException;
import com.devlensai.backend.exception.AiProviderTimeoutException;
import com.devlensai.backend.exception.AnalysisReviewFailedException;
import com.devlensai.backend.repository.AnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisServiceTest {

    private AnalysisRepository repository;
    private CodeReviewService codeReviewService;
    private AnalysisService analysisService;
    private List<AnalysisStatus> savedStatuses;

    @BeforeEach
    void setUp() {
        repository = mock(AnalysisRepository.class);
        codeReviewService = mock(CodeReviewService.class);
        savedStatuses = new ArrayList<>();
        when(repository.save(any(Analysis.class))).thenAnswer(invocation -> {
            Analysis analysis = invocation.getArgument(0);
            savedStatuses.add(analysis.getStatus());
            return analysis;
        });
        analysisService = new AnalysisService(repository, codeReviewService);
    }

    @Test
    void savesPendingThenStoresCompletedStructuredResult() {
        CodeReviewResult result = result();
        when(codeReviewService.review(ProgrammingLanguage.JAVA, "class Main {}"))
                .thenReturn(result);

        AnalysisResponse response = analysisService.create(request());

        assertThat(savedStatuses).containsExactly(AnalysisStatus.PENDING, AnalysisStatus.COMPLETED);
        assertThat(response.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(response.result()).isEqualTo(result);
        assertThat(response.failureReason()).isNull();
        verify(repository, times(2)).save(any(Analysis.class));
    }

    @Test
    void savesFailedAnalysisAndReturnsClearTimeoutError() {
        when(codeReviewService.review(ProgrammingLanguage.JAVA, "class Main {}"))
                .thenThrow(new AiProviderTimeoutException(
                        "provider timeout", new HttpTimeoutException("timed out")
                ));

        assertThatThrownBy(() -> analysisService.create(request()))
                .isInstanceOf(AnalysisReviewFailedException.class)
                .hasMessageContaining("saved as FAILED")
                .hasMessageContaining("AI provider request timed out");

        assertThat(savedStatuses).containsExactly(AnalysisStatus.PENDING, AnalysisStatus.FAILED);
        ArgumentCaptor<Analysis> captor = ArgumentCaptor.forClass(Analysis.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getFailureReason())
                .isEqualTo("AI provider request timed out");
    }

    @Test
    void savesFailedAnalysisWhenProviderResponseIsMalformed() {
        when(codeReviewService.review(ProgrammingLanguage.JAVA, "class Main {}"))
                .thenThrow(new AiProviderMalformedResponseException("invalid response"));

        assertThatThrownBy(() -> analysisService.create(request()))
                .isInstanceOf(AnalysisReviewFailedException.class)
                .hasMessageContaining("AI provider returned an invalid response");

        assertThat(savedStatuses).containsExactly(AnalysisStatus.PENDING, AnalysisStatus.FAILED);
    }

    private CreateAnalysisRequest request() {
        return new CreateAnalysisRequest(ProgrammingLanguage.JAVA, "class Main {}");
    }

    private CodeReviewResult result() {
        return new CodeReviewResult(
                "Review summary",
                List.of("Potential bug"),
                "O(1)",
                "O(1)",
                List.of("Empty input"),
                List.of("Add documentation"),
                "class Main {}"
        );
    }
}
