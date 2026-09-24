package com.devlensai.backend.ai;

import com.devlensai.backend.dto.CodeReviewResult;
import com.devlensai.backend.dto.RepositoryEvidenceReference;
import com.devlensai.backend.dto.RepositorySummaryResult;
import com.devlensai.backend.entity.ProgrammingLanguage;

/** Local-only boundary for untrusted repository source. */
public interface RepositoryAnalysisProvider {
    void validateSelection(String profileId, String model);
    CodeReviewResult analyze(ProgrammingLanguage language, String source, String profileId, String model);
    RepositorySummaryResult summarize(String level, String identity, String untrustedContent,
                                      java.util.List<RepositoryEvidenceReference> allowedEvidence,
                                      String profileId, String model);
    String providerName();
}
