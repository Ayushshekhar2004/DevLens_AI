package com.devlensai.backend.dto;

import com.devlensai.backend.entity.ProgrammingLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAnalysisRequest(
        @NotNull(message = "language is required")
        ProgrammingLanguage language,

        @NotBlank(message = "sourceCode must not be blank")
        String sourceCode
) {
}
