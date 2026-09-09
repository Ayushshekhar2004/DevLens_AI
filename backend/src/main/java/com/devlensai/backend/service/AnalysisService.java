package com.devlensai.backend.service;

import com.devlensai.backend.dto.AnalysisResponse;
import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.dto.CreateAnalysisRequest;
import com.devlensai.backend.entity.Analysis;
import com.devlensai.backend.exception.AnalysisNotFoundException;
import com.devlensai.backend.exception.AnalysisReviewFailedException;
import com.devlensai.backend.exception.AiProviderApiException;
import com.devlensai.backend.exception.AiProviderException;
import com.devlensai.backend.exception.AiProviderMalformedResponseException;
import com.devlensai.backend.exception.AiProviderTimeoutException;
import com.devlensai.backend.exception.AiProviderUnavailableException;
import com.devlensai.backend.repository.AnalysisRepository;
import org.springframework.http.HttpStatus;
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

    public AnalysisResponse create(CreateAnalysisRequest request) {
        Analysis analysis = analysisRepository.save(
                new Analysis(request.language(), request.sourceCode())
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
    public AnalysisResponse findById(Long id) {
        return analysisRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new AnalysisNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> findAllNewestFirst() {
        return analysisRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
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
