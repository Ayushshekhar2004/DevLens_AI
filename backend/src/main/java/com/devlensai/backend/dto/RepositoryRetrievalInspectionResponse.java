package com.devlensai.backend.dto;

import java.util.List;

public record RepositoryRetrievalInspectionResponse(String purpose, String snapshotHash,
        int usableContextTokens, int usedContextTokens, boolean truncated, boolean missingContext,
        List<Selected> selected, List<Decision> excluded, List<String> unresolvedRelationships) {
    public record Selected(String relativePath, String fileHash, int startLine, int endLine,
                           int estimatedTokens, int score, List<String> reasons, String summaryHint) { }
    public record Decision(String relativePath, String reason) { }
}
