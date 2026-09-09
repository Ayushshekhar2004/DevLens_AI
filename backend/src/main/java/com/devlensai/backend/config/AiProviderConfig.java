package com.devlensai.backend.config;

import com.devlensai.backend.ai.AiCodeReviewProvider;
import com.devlensai.backend.ai.MockAiCodeReviewProvider;
import com.devlensai.backend.ai.OpenAiCompatibleCodeReviewProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
public class AiProviderConfig {

    @Bean
    public AiCodeReviewProvider aiCodeReviewProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.provider}") String provider,
            @Value("${app.ai.api-key}") String apiKey,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.model}") String model,
            @Value("${app.ai.timeout-seconds}") long timeoutSeconds
    ) {
        String selectedProvider = provider.trim().toLowerCase();

        if ("mock".equals(selectedProvider) || ("auto".equals(selectedProvider) && apiKey.isBlank())) {
            return new MockAiCodeReviewProvider();
        }
        if (!"auto".equals(selectedProvider) && !"openai-compatible".equals(selectedProvider)) {
            throw new IllegalArgumentException(
                    "Unsupported AI_PROVIDER. Use auto, mock, or openai-compatible."
            );
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("AI_API_KEY is required when the real AI provider is selected");
        }

        return new OpenAiCompatibleCodeReviewProvider(
                objectMapper,
                apiKey,
                baseUrl,
                model,
                Duration.ofSeconds(timeoutSeconds)
        );
    }
}
