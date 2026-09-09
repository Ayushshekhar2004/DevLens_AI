package com.devlensai.backend.service;

import com.devlensai.backend.ai.AiCodeReviewProvider;
import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.entity.ProgrammingLanguage;
import org.springframework.stereotype.Service;

@Service
public class DefaultCodeReviewService implements CodeReviewService {

    private final AiCodeReviewProvider provider;

    public DefaultCodeReviewService(AiCodeReviewProvider provider) {
        this.provider = provider;
    }

    @Override
    public CodeReviewResult review(ProgrammingLanguage language, String sourceCode) {
        if (language == null) {
            throw new IllegalArgumentException("language is required");
        }
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("sourceCode must not be blank");
        }

        return provider.review(language, sourceCode);
    }
}
