package com.devlensai.backend.dto;

import java.util.List;

public record RepositoryFindingCandidate(
        String category,
        String severity,
        String confidence,
        String claim,
        String rationale,
        RepositoryEvidenceReference primaryLocation,
        List<RepositoryEvidenceReference> relatedEvidence,
        String suggestedRemediation
) {
    public RepositoryFindingCandidate {
        relatedEvidence = relatedEvidence == null ? List.of() : List.copyOf(relatedEvidence);
    }
}
