package com.devlensai.backend.ai;

import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.entity.ProgrammingLanguage;

public interface AiCodeReviewProvider {

    CodeReviewResult review(ProgrammingLanguage language, String sourceCode);

    String providerName();
}
