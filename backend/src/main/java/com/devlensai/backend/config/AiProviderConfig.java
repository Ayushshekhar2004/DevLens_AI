package com.devlensai.backend.config;

import com.devlensai.backend.ai.AiCodeReviewProvider;
import com.devlensai.backend.ai.MockAiCodeReviewProvider;
import com.devlensai.backend.ai.OpenAiCompatibleCodeReviewProvider;
import com.devlensai.backend.ai.OllamaAiProvider;
import com.devlensai.backend.ai.OllamaConnections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
public class AiProviderConfig {

    @Bean
    public OllamaConnections ollamaConnections(@Value("${app.ollama.profiles}") String profiles) {
        return new OllamaConnections(profiles);
    }

    @Bean
    public AiCodeReviewProvider configuredAiCodeReviewProvider(
            ObjectMapper objectMapper,
            OllamaConnections connections,
            @Value("${app.ai.provider}") String provider,
            @Value("${app.ai.api-key}") String apiKey,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.model}") String model,
            @Value("${app.ai.timeout-seconds}") long timeoutSeconds,
            @Value("${app.ollama.default-profile}") String ollamaProfile,
            @Value("${app.ollama.model}") String ollamaModel,
            @Value("${app.ollama.connect-timeout-seconds}") long connectTimeoutSeconds,
            @Value("${app.ollama.read-timeout-seconds}") long readTimeoutSeconds,
            @Value("${app.ollama.output-limit}") int outputLimit,
            @Value("${app.ollama.context-budget}") int contextBudget
    ) {
        if ("ollama".equalsIgnoreCase(provider.trim())) {
            if (connectTimeoutSeconds < 1 || connectTimeoutSeconds > 60
                    || readTimeoutSeconds < 1 || readTimeoutSeconds > 600
                    || ollamaModel == null || ollamaModel.isBlank()) {
                throw new IllegalArgumentException("Invalid Ollama provider configuration");
            }
            return new OllamaAiProvider(objectMapper, connections, ollamaProfile, ollamaModel,
                    Duration.ofSeconds(connectTimeoutSeconds), Duration.ofSeconds(readTimeoutSeconds),
                    outputLimit, contextBudget);
        }
        return aiCodeReviewProvider(objectMapper, provider, apiKey, baseUrl, model, timeoutSeconds);
    }

    public AiCodeReviewProvider aiCodeReviewProvider(ObjectMapper objectMapper, String provider,
            String apiKey, String baseUrl, String model, long timeoutSeconds) {
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
