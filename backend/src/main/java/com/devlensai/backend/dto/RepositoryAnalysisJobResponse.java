package com.devlensai.backend.dto;

import com.devlensai.backend.entity.RepositoryAnalysisJob;
import com.devlensai.backend.entity.RepositoryAnalysisStage;

import java.time.Instant;
import java.util.List;

public record RepositoryAnalysisJobResponse(
        Long id, Long snapshotId, String status, String currentStage, int progress,
        String provider, String profileId, String model, String snapshotHash,
        String promptVersion, String parserVersion, String schemaVersion,
        int totalUnits, int analyzedUnits, int skippedUnits,
        BudgetUsage budgetUsage, String errorMessage, Instant deadlineAt,
        Instant createdAt, Instant updatedAt, List<Stage> stages) {
    public static RepositoryAnalysisJobResponse from(RepositoryAnalysisJob job, List<RepositoryAnalysisStage> stages) {
        return new RepositoryAnalysisJobResponse(job.getId(), job.getSnapshot().getId(), job.getStatus().name(),
                job.getCurrentStage().name(), job.getProgress(), job.getProviderName(), job.getProfileId(),
                job.getModelName(), job.getSnapshotHash(), job.getPromptVersion(), job.getParserVersion(),
                job.getSchemaVersion(), job.getTotalUnits(), job.getAnalyzedUnits(), job.getSkippedUnits(),
                new BudgetUsage(job.getUsedCalls(), job.getUsedInputTokens(), job.getUsedOutputTokens()),
                job.getErrorMessage(), job.getDeadlineAt(), job.getCreatedAt(), job.getUpdatedAt(),
                stages.stream().map(Stage::from).toList());
    }
    public record BudgetUsage(int calls, int estimatedInputTokens, int estimatedOutputTokens) { }
    public record Stage(String name, String status, Instant startedAt, Instant completedAt, String errorMessage) {
        static Stage from(RepositoryAnalysisStage stage) { return new Stage(stage.getType().name(), stage.getStatus().name(), stage.getStartedAt(), stage.getCompletedAt(), stage.getErrorMessage()); }
    }
}
