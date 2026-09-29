package com.devlensai.backend.ai;

import com.devlensai.backend.entity.ProgrammingLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit opt-in live check; sends only synthetic code, never project files. */
@EnabledIfEnvironmentVariable(named = "DEVLENS_GEMINI_SMOKE", matches = "true")
class GeminiSnippetSmokeTest {
    @Test
    void geminiReturnsAReviewAcceptedByTheApplicationParser() {
        var provider = new OpenAiCompatibleCodeReviewProvider(
                new ObjectMapper(), System.getenv("AI_API_KEY"),
                "https://generativelanguage.googleapis.com/v1beta/openai/",
                System.getenv("AI_MODEL"), Duration.ofSeconds(120));
        var result = provider.review(ProgrammingLanguage.JAVA,
                "class Example { static int first(int[] values) { return values[0]; } }");
        assertThat(result.summary()).isNotBlank();
        assertThat(result.generatedTestCases()).isNotEmpty();
    }
}
