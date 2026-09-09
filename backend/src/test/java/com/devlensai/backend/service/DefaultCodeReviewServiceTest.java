package com.devlensai.backend.service;

import com.devlensai.backend.ai.MockAiCodeReviewProvider;
import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.entity.ProgrammingLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCodeReviewServiceTest {

    private final DefaultCodeReviewService service =
            new DefaultCodeReviewService(new MockAiCodeReviewProvider());

    @Test
    void returnsClearlyLabeledStructuredMockResult() {
        String sourceCode = "public class Main {}";

        CodeReviewResult result = service.review(ProgrammingLanguage.JAVA, sourceCode);

        assertThat(result.summary()).startsWith("[MOCK REVIEW]");
        assertThat(result.potentialBugs()).isNotEmpty();
        assertThat(result.timeComplexity()).isNotBlank();
        assertThat(result.spaceComplexity()).isNotBlank();
        assertThat(result.edgeCases()).isNotEmpty();
        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.improvedCode()).isEqualTo(sourceCode);
    }

    @Test
    void rejectsBlankSourceCodeBeforeCallingProvider() {
        assertThatThrownBy(() -> service.review(ProgrammingLanguage.JAVA, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceCode must not be blank");
    }
}
