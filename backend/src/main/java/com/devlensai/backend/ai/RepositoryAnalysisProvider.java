package com.devlensai.backend.ai;

import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.dto.RepositoryEvidenceReference;
import com.devlensai.backend.dto.RepositorySummaryResult;
import com.devlensai.backend.dto.RepositoryReviewResult;
import com.devlensai.backend.entity.ProgrammingLanguage;

/** Explicit provider boundary for untrusted repository source; no automatic cloud fallback. */
public interface RepositoryAnalysisProvider {
    default boolean requiresCloudConsent() { return false; }
    void validateSelection(String profileId, String model);
    CodeReviewResult analyze(ProgrammingLanguage language, String source, String profileId, String model);
    RepositorySummaryResult summarize(String level, String identity, String untrustedContent,
                                      java.util.List<RepositoryEvidenceReference> allowedEvidence,
                                      String profileId, String model);
    RepositoryReviewResult reviewRepository(String target, String untrustedContext,
                                            java.util.List<RepositoryEvidenceReference> allowedEvidence,
                                            String profileId, String model);
    String providerName();
}
