package com.devlensai.backend.dto;

import com.devlensai.backend.entity.TestCaseCategory;

public record GeneratedTestCaseResult(
        String name,
        TestCaseCategory category,
        String input,
        String expectedOutput,
        String explanation,
        String confidenceOrWarning
) {
}
