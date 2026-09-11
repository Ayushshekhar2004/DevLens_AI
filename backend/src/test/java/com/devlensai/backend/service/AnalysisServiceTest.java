package com.devlensai.backend.service;

import com.devlensai.backend.dto.AnalysisResponse;
import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.dto.CreateAnalysisRequest;
import com.devlensai.backend.dto.GeneratedTestCaseResult;
import com.devlensai.backend.dto.SecurityFindingResult;
import com.devlensai.backend.entity.Analysis;
import com.devlensai.backend.entity.AnalysisStatus;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.entity.TestCaseCategory;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.entity.SecuritySeverity;
import com.devlensai.backend.exception.AiProviderMalformedResponseException;
import com.devlensai.backend.exception.AiProviderTimeoutException;
import com.devlensai.backend.exception.AnalysisReviewFailedException;
import com.devlensai.backend.exception.AnalysisNotFoundException;
import com.devlensai.backend.exception.InvalidHistoryQueryException;
import com.devlensai.backend.repository.AnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class AnalysisServiceTest {

    private AnalysisRepository repository;
    private CodeReviewService codeReviewService;
    private AnalysisService analysisService;
    private List<AnalysisStatus> savedStatuses;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(AnalysisRepository.class);
        codeReviewService = mock(CodeReviewService.class);
        savedStatuses = new ArrayList<>();
        user = new User("Ada Lovelace", "ada@example.com", "password-hash");
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

        AnalysisResponse response = analysisService.create(user, request());

        assertThat(savedStatuses).containsExactly(AnalysisStatus.PENDING, AnalysisStatus.COMPLETED);
        assertThat(response.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(response.result()).isEqualTo(result);
        assertThat(response.failureReason()).isNull();
        ArgumentCaptor<Analysis> captor = ArgumentCaptor.forClass(Analysis.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().getFirst().getUser()).isSameAs(user);
    }

    @Test
    void savesFailedAnalysisAndReturnsClearTimeoutError() {
        when(codeReviewService.review(ProgrammingLanguage.JAVA, "class Main {}"))
                .thenThrow(new AiProviderTimeoutException(
                        "provider timeout", new HttpTimeoutException("timed out")
                ));

        assertThatThrownBy(() -> analysisService.create(user, request()))
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

        assertThatThrownBy(() -> analysisService.create(user, request()))
                .isInstanceOf(AnalysisReviewFailedException.class)
                .hasMessageContaining("AI provider returned an invalid response");

        assertThat(savedStatuses).containsExactly(AnalysisStatus.PENDING, AnalysisStatus.FAILED);
    }

    @Test
    void scopesReadsToAuthenticatedUser() {
        User authenticatedUser = mock(User.class);
        when(authenticatedUser.getId()).thenReturn(42L);
        when(repository.findAllByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of());
        when(repository.findByIdAndUserId(7L, 42L)).thenReturn(Optional.empty());

        assertThat(analysisService.findAllNewestFirst(authenticatedUser)).isEmpty();
        assertThatThrownBy(() -> analysisService.findById(authenticatedUser, 7L))
                .isInstanceOf(AnalysisNotFoundException.class);

        verify(repository).findAllByUserIdOrderByCreatedAtDesc(42L);
        verify(repository).findByIdAndUserId(7L, 42L);
    }

    @Test
    void paginatesHistoryWithRequestedSort() {
        User authenticatedUser = mock(User.class);
        when(authenticatedUser.getId()).thenReturn(42L);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));

        var response = analysisService.findHistory(
                authenticatedUser, 2, 15, "Main", ProgrammingLanguage.JAVA, "oldest"
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(15);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidHistoryPaginationBeforeQueryingDatabase() {
        assertThatThrownBy(() -> analysisService.findHistory(user, -1, 20, null, null, "newest"))
                .isInstanceOf(InvalidHistoryQueryException.class)
                .hasMessage("page must be zero or greater");
        assertThatThrownBy(() -> analysisService.findHistory(user, 0, 101, null, null, "newest"))
                .isInstanceOf(InvalidHistoryQueryException.class)
                .hasMessage("size must be between 1 and 100");
        assertThatThrownBy(() -> analysisService.findHistory(user, 0, 20, null, null, "sideways"))
                .isInstanceOf(InvalidHistoryQueryException.class)
                .hasMessage("sort must be either newest or oldest");

        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void deletesOnlyAnalysisOwnedByAuthenticatedUser() {
        User authenticatedUser = mock(User.class);
        Analysis ownedAnalysis = mock(Analysis.class);
        when(authenticatedUser.getId()).thenReturn(42L);
        when(repository.findByIdAndUserId(7L, 42L)).thenReturn(Optional.of(ownedAnalysis));

        analysisService.delete(authenticatedUser, 7L);

        verify(repository).delete(ownedAnalysis);
    }

    @Test
    void hidesUnownedAnalysisDuringDelete() {
        User authenticatedUser = mock(User.class);
        when(authenticatedUser.getId()).thenReturn(42L);
        when(repository.findByIdAndUserId(7L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.delete(authenticatedUser, 7L))
                .isInstanceOf(AnalysisNotFoundException.class);

        verify(repository, never()).delete(any(Analysis.class));
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
                "class Main {}",
                List.of(new GeneratedTestCaseResult(
                        "Creates an instance",
                        TestCaseCategory.NORMAL,
                        "new Main()",
                        "A Main instance",
                        "Covers normal construction",
                        "High confidence"
                )),
                List.of(new SecurityFindingResult(
                        "Unsafe input flow",
                        SecuritySeverity.HIGH,
                        "Untrusted input reaches a sensitive operation.",
                        "Main.java:12",
                        "Validate and constrain the input.",
                        "Medium confidence because surrounding code is unavailable."
                ))
        );
    }
}
