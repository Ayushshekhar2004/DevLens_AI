package com.devlensai.backend.service;

import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.entity.ProgrammingLanguage;

public interface CodeReviewService {

    CodeReviewResult review(ProgrammingLanguage language, String sourceCode);
}
