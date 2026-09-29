package com.devlensai.backend.dto;

import com.devlensai.backend.entity.ProgrammingLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAnalysisRequest(
        @NotNull(message = "language is required")
        ProgrammingLanguage language,

        @NotBlank(message = "sourceCode must not be blank")
        @Size(max = 20000, message = "sourceCode must not exceed 20,000 characters; submit a focused snippet")
        String sourceCode
) {
}
