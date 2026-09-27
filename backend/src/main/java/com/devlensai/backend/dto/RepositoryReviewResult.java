package com.devlensai.backend.dto;

import java.util.List;

public record RepositoryReviewResult(boolean insufficientContext, String uncertainty,
                                     List<RepositoryFindingCandidate> findings) {
    public RepositoryReviewResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
