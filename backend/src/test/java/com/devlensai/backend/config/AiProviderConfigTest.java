package com.devlensai.backend.config;

import com.devlensai.backend.ai.AiCodeReviewProvider;
import com.devlensai.backend.ai.MockAiCodeReviewProvider;
import com.devlensai.backend.ai.OpenAiCompatibleCodeReviewProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderConfigTest {

    private final AiProviderConfig config = new AiProviderConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void autoUsesMockWhenApiKeyIsMissing() {
        AiCodeReviewProvider provider = config.aiCodeReviewProvider(
                objectMapper, "auto", "", "https://example.com/v1/", "model", 30
        );

        assertThat(provider).isInstanceOf(MockAiCodeReviewProvider.class);
    }

    @Test
    void explicitMockDoesNotRequireApiKey() {
        AiCodeReviewProvider provider = config.aiCodeReviewProvider(
                objectMapper, "mock", "", "https://example.com/v1/", "model", 30
        );

        assertThat(provider).isInstanceOf(MockAiCodeReviewProvider.class);
    }

    @Test
    void configuredKeyUsesRealProvider() {
        AiCodeReviewProvider provider = config.aiCodeReviewProvider(
                objectMapper, "auto", "test-key", "https://example.com/v1/", "model", 30
        );

        assertThat(provider).isInstanceOf(OpenAiCompatibleCodeReviewProvider.class);
    }

    @Test
    void realProviderRequiresApiKey() {
        assertThatThrownBy(() -> config.aiCodeReviewProvider(
                objectMapper, "openai-compatible", "", "https://example.com/v1/", "model", 30
        )).isInstanceOf(IllegalStateException.class);
    }
}
