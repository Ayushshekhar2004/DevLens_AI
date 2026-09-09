package com.devlensai.backend.ai;

import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.entity.ProgrammingLanguage;

import java.util.List;

public class MockAiCodeReviewProvider implements AiCodeReviewProvider {

    private static final String PROVIDER_NAME = "mock";

    @Override
    public CodeReviewResult review(ProgrammingLanguage language, String sourceCode) {
        return new CodeReviewResult(
                "[MOCK REVIEW] Local placeholder review for " + language + ". No external AI was called.",
                List.of("Mock provider: potential bugs were not evaluated."),
                "Not evaluated by the mock provider",
                "Not evaluated by the mock provider",
                List.of("Mock provider: edge cases were not evaluated."),
                List.of("Configure a real AI provider in a future implementation for code-specific feedback."),
                sourceCode
        );
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }
}
