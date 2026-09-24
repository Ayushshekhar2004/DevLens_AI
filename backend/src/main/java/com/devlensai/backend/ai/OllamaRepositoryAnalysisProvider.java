package com.devlensai.backend.ai;

import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.dto.RepositoryEvidenceReference;
import com.devlensai.backend.dto.RepositorySummaryResult;
import com.devlensai.backend.entity.ProgrammingLanguage;
import com.devlensai.backend.exception.OllamaSelectionException;
import com.devlensai.backend.config.RepositoryAnalysisProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Component
public class OllamaRepositoryAnalysisProvider implements RepositoryAnalysisProvider {
    private final OllamaAiProvider delegate;

    public OllamaRepositoryAnalysisProvider(ObjectMapper mapper, OllamaConnections connections,
            @Value("${app.ollama.default-profile}") String defaultProfile,
            @Value("${app.ollama.model}") String defaultModel,
            @Value("${app.ollama.connect-timeout-seconds}") long connectTimeout,
            @Value("${app.repository-analysis.call-timeout-seconds}") long callTimeout,
            RepositoryAnalysisProperties properties) {
        this.delegate = new OllamaAiProvider(mapper, connections, defaultProfile, defaultModel,
                Duration.ofSeconds(connectTimeout), Duration.ofSeconds(callTimeout),
                properties.outputReserveTokens(), properties.contextTokens(), true);
    }

    @Override
    public void validateSelection(String profileId, String model) {
        if (model == null || model.isBlank()) throw new OllamaSelectionException("Select an installed Ollama model");
        if (!delegate.installedModels(profileId).contains(model)) {
            throw new OllamaSelectionException("Selected Ollama model is no longer installed on this connection");
        }
    }

    @Override
    public CodeReviewResult analyze(ProgrammingLanguage language, String source, String profileId, String model) {
        return delegate.reviewValidated(language, source, profileId, model);
    }

    @Override
    public RepositorySummaryResult summarize(String level, String identity, String untrustedContent,
            java.util.List<RepositoryEvidenceReference> allowedEvidence, String profileId, String model) {
        return delegate.summarizeRepositoryValidated(level, identity, untrustedContent, allowedEvidence, profileId, model);
    }

    @Override public String providerName() { return delegate.providerName(); }
}
