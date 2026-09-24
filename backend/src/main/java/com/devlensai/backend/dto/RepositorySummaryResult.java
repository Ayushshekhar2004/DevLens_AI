package com.devlensai.backend.dto;

import java.util.List;

public record RepositorySummaryResult(
        String responsibilities,
        List<String> keySymbols,
        List<String> dependencies,
        String uncertainty,
        List<RepositoryEvidenceReference> evidence) { }
