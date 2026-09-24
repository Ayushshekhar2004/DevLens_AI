package com.devlensai.backend.dto;

import com.devlensai.backend.entity.RepositorySummary;
import java.util.List;

public record RepositorySummaryResponse(
        String level, String identity, String status, String responsibilities,
        List<String> keySymbols, List<String> dependencies, String uncertainty,
        List<RepositoryEvidenceReference> evidence, boolean cacheHit, String errorMessage) {
    public static RepositorySummaryResponse from(RepositorySummary value) {
        return new RepositorySummaryResponse(value.getLevel().name(), value.getIdentity(), value.getStatus(),
                value.getResponsibilities(), value.getKeySymbols(), value.getDependencies(), value.getUncertainty(),
                value.getEvidence(), value.isCacheHit(), value.getErrorMessage());
    }
}
