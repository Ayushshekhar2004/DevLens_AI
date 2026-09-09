package com.devlensai.backend.dto;

import com.devlensai.backend.entity.AnalysisStatus;
import com.devlensai.backend.entity.ProgrammingLanguage;

import java.time.Instant;

public record AnalysisResponse(
        Long id,
        ProgrammingLanguage language,
        String sourceCode,
        AnalysisStatus status,
        Instant createdAt,
        CodeReviewResult result,
        String failureReason
) {
}
