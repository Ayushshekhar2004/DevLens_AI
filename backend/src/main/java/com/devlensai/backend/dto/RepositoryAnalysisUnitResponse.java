package com.devlensai.backend.dto;

import com.devlensai.backend.entity.RepositoryAnalysisUnit;

public record RepositoryAnalysisUnitResponse(
        String chunkId, String relativePath, String fileHash, int startLine, int endLine,
        int sequence, String status, int estimatedInputTokens, int estimatedOutputTokens,
        String summary, String errorMessage) {
    public static RepositoryAnalysisUnitResponse from(RepositoryAnalysisUnit unit) {
        return new RepositoryAnalysisUnitResponse(unit.getChunkId(), unit.getRelativePath(), unit.getFileHash(),
                unit.getStartLine(), unit.getEndLine(), unit.getSequence(), unit.getStatus().name(),
                unit.getEstimatedInputTokens(), unit.getEstimatedOutputTokens(), unit.getResultSummary(), unit.getErrorMessage());
    }
}
