package com.devlensai.backend.entity;

import com.devlensai.backend.dto.GeneratedTestCaseResult;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class GeneratedTestCase {

    @Column(name = "test_name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private TestCaseCategory category;

    @Column(name = "test_input", nullable = false, columnDefinition = "TEXT")
    private String input;

    @Column(name = "expected_output", nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "confidence_or_warning", nullable = false, columnDefinition = "TEXT")
    private String confidenceOrWarning;

    protected GeneratedTestCase() {
    }

    public GeneratedTestCase(GeneratedTestCaseResult result) {
        this.name = result.name();
        this.category = result.category();
        this.input = result.input();
        this.expectedOutput = result.expectedOutput();
        this.explanation = result.explanation();
        this.confidenceOrWarning = result.confidenceOrWarning();
    }

    public GeneratedTestCaseResult toResult() {
        return new GeneratedTestCaseResult(
                name,
                category,
                input,
                expectedOutput,
                explanation,
                confidenceOrWarning
        );
    }
}
