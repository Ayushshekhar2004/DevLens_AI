package com.devlensai.backend.ai;

import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.exception.OllamaSelectionException;

public interface AiCodeReviewProvider {

    CodeReviewResult review(ProgrammingLanguage language, String sourceCode);

    default CodeReviewResult review(ProgrammingLanguage language, String sourceCode,
                                   String profileId, String model) {
        if (profileId != null || model != null) {
            throw new OllamaSelectionException("Ollama provider is not enabled");
        }
        return review(language, sourceCode);
    }

    String providerName();
}
