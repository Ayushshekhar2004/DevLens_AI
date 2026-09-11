package com.devlensai.backend.service;

import com.devlensai.backend.dto.AnalysisResponse;
import com.devlensai.backend.dto.AnalysisHistoryResponse;
import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.dto.CreateAnalysisRequest;
import com.devlensai.backend.entity.Analysis;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.entity.User;
import com.devlensai.backend.exception.AnalysisNotFoundException;
import com.devlensai.backend.exception.AnalysisReviewFailedException;
import com.devlensai.backend.exception.InvalidHistoryQueryException;
import com.devlensai.backend.exception.AiProviderApiException;
import com.devlensai.backend.exception.AiProviderException;
import com.devlensai.backend.exception.AiProviderMalformedResponseException;
import com.devlensai.backend.exception.AiProviderTimeoutException;
import com.devlensai.backend.exception.AiProviderUnavailableException;
import com.devlensai.backend.repository.AnalysisRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final CodeReviewService codeReviewService;

    public AnalysisService(AnalysisRepository analysisRepository, CodeReviewService codeReviewService) {
        this.analysisRepository = analysisRepository;
        this.codeReviewService = codeReviewService;
    }

    public AnalysisResponse create(User user, CreateAnalysisRequest request) {
        Analysis analysis = analysisRepository.save(
                new Analysis(user, request.language(), request.sourceCode())
        );

        CodeReviewResult result;
        try {
            result = codeReviewService.review(request.language(), request.sourceCode());
        } catch (AiProviderException exception) {
            FailureDetails failure = failureDetails(exception);
            analysis.markFailed(failure.reason());
            analysisRepository.save(analysis);
            throw new AnalysisReviewFailedException(
                    analysis.getId(), failure.status(), failure.reason(), exception
            );
        } catch (RuntimeException exception) {
            String reason = "AI review failed unexpectedly";
            analysis.markFailed(reason);
            analysisRepository.save(analysis);
            throw new AnalysisReviewFailedException(
                    analysis.getId(), HttpStatus.BAD_GATEWAY, reason, exception
            );
        }

        analysis.completeWith(result);
        return toResponse(analysisRepository.save(analysis));
    }

    @Transactional(readOnly = true)
    public AnalysisResponse findById(User user, Long id) {
        return analysisRepository.findByIdAndUserId(id, user.getId())
                .map(this::toResponse)
                .orElseThrow(() -> new AnalysisNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> findAllNewestFirst(User user) {
        return analysisRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalysisHistoryResponse findHistory(
            User user,
            int page,
            int size,
            String search,
            ProgrammingLanguage language,
            String sort
    ) {
        validateHistoryRequest(page, size, search);
        Sort.Direction direction = historySortDirection(sort);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, "createdAt"));

        Specification<Analysis> filters = ownedBy(user.getId())
                .and(hasLanguage(language))
                .and(containsText(search));
        Page<AnalysisResponse> results = analysisRepository.findAll(filters, pageRequest)
                .map(this::toResponse);

        return new AnalysisHistoryResponse(
                results.getContent(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.isFirst(),
                results.isLast()
        );
    }

    @Transactional
    public void delete(User user, Long id) {
        Analysis analysis = analysisRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AnalysisNotFoundException(id));
        analysisRepository.delete(analysis);
    }

    private Specification<Analysis> ownedBy(Long userId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("user").get("id"), userId);
    }

    private Specification<Analysis> hasLanguage(ProgrammingLanguage language) {
        return language == null
                ? null
                : (root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("programmingLanguage"), language);
    }

    private Specification<Analysis> containsText(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("sourceCode")), pattern),
                criteriaBuilder.like(
                        criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("summary"), "")),
                        pattern
                )
        );
    }

    private void validateHistoryRequest(int page, int size, String search) {
        if (page < 0) {
            throw new InvalidHistoryQueryException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new InvalidHistoryQueryException("size must be between 1 and 100");
        }
        if (search != null && search.length() > 200) {
            throw new InvalidHistoryQueryException("search must not exceed 200 characters");
        }
    }

    private Sort.Direction historySortDirection(String sort) {
        if ("newest".equalsIgnoreCase(sort)) {
            return Sort.Direction.DESC;
        }
        if ("oldest".equalsIgnoreCase(sort)) {
            return Sort.Direction.ASC;
        }
        throw new InvalidHistoryQueryException("sort must be either newest or oldest");
    }

    private AnalysisResponse toResponse(Analysis analysis) {
        return new AnalysisResponse(
                analysis.getId(),
                analysis.getProgrammingLanguage(),
                analysis.getSourceCode(),
                analysis.getStatus(),
                analysis.getCreatedAt(),
                analysis.getResult(),
                analysis.getFailureReason()
        );
    }

    private FailureDetails failureDetails(AiProviderException exception) {
        if (exception instanceof AiProviderTimeoutException) {
            return new FailureDetails(HttpStatus.GATEWAY_TIMEOUT, "AI provider request timed out");
        }
        if (exception instanceof AiProviderUnavailableException) {
            return new FailureDetails(HttpStatus.SERVICE_UNAVAILABLE, "AI provider is unavailable");
        }
        if (exception instanceof AiProviderMalformedResponseException) {
            return new FailureDetails(HttpStatus.BAD_GATEWAY, "AI provider returned an invalid response");
        }
        if (exception instanceof AiProviderApiException) {
            return new FailureDetails(HttpStatus.BAD_GATEWAY, "AI provider rejected the review request");
        }
        return new FailureDetails(HttpStatus.BAD_GATEWAY, "AI review failed");
    }

    private record FailureDetails(HttpStatus status, String reason) {
    }
}
