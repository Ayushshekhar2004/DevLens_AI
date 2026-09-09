package com.devlensai.backend.exception;

import org.springframework.http.HttpStatus;

public class AnalysisReviewFailedException extends RuntimeException {

    private final HttpStatus responseStatus;

    public AnalysisReviewFailedException(
            Long analysisId,
            HttpStatus responseStatus,
            String reason,
            Throwable cause
    ) {
        super("Analysis " + analysisId + " was saved as FAILED: " + reason, cause);
        this.responseStatus = responseStatus;
    }

    public HttpStatus getResponseStatus() {
        return responseStatus;
    }
}
