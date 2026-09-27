package com.devlensai.backend.service;

import java.util.List;

public record RepositoryContextPack(String purpose, String snapshotHash, int usableContextTokens,
        int usedContextTokens, boolean truncated, boolean missingContext, List<Snippet> snippets,
        List<Decision> excluded, List<String> unresolvedRelationships) {
    public record Snippet(String relativePath, String fileHash, int startLine, int endLine, String content,
                          int estimatedTokens, int score, List<String> reasons, String summaryHint) { }
    public record Decision(String relativePath, String reason) { }
}
