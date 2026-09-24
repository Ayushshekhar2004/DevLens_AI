package com.devlensai.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "repository_analysis_jobs", indexes = {
        @Index(name = "idx_repo_analysis_owner_created", columnList = "user_id,created_at"),
        @Index(name = "idx_repo_analysis_snapshot_status", columnList = "snapshot_id,status")
})
public class RepositoryAnalysisJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "snapshot_id", nullable = false) private RepositorySnapshot snapshot;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private RepositoryAnalysisJobStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "current_stage", nullable = false, length = 16) private RepositoryAnalysisStageType currentStage;
    @Column(name = "snapshot_hash", nullable = false, length = 64) private String snapshotHash;
    @Column(name = "provider_name", nullable = false, length = 32) private String providerName;
    @Column(name = "profile_id", nullable = false, length = 32) private String profileId;
    @Column(name = "model_name", nullable = false, length = 128) private String modelName;
    @Column(name = "prompt_version", nullable = false, length = 32) private String promptVersion;
    @Column(name = "parser_version", nullable = false, length = 32) private String parserVersion;
    @Column(name = "schema_version", nullable = false, length = 32) private String schemaVersion;
    @Column(nullable = false) private int progress;
    @Column(name = "total_units", nullable = false) private int totalUnits;
    @Column(name = "analyzed_units", nullable = false) private int analyzedUnits;
    @Column(name = "skipped_units", nullable = false) private int skippedUnits;
    @Column(name = "used_calls", nullable = false) private int usedCalls;
    @Column(name = "used_input_tokens", nullable = false) private int usedInputTokens;
    @Column(name = "used_output_tokens", nullable = false) private int usedOutputTokens;
    @Column(name = "cancel_requested", nullable = false) private boolean cancelRequested;
    @Column(name = "error_message", length = 512) private String errorMessage;
    @Column(name = "deadline_at", nullable = false) private Instant deadlineAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected RepositoryAnalysisJob() { }

    public RepositoryAnalysisJob(User user, RepositorySnapshot snapshot, String snapshotHash,
            String providerName, String profileId, String modelName, String promptVersion,
            String parserVersion, String schemaVersion, Instant deadlineAt) {
        this.user = user; this.snapshot = snapshot; this.snapshotHash = snapshotHash;
        this.providerName = providerName; this.profileId = profileId; this.modelName = modelName;
        this.promptVersion = promptVersion; this.parserVersion = parserVersion;
        this.schemaVersion = schemaVersion; this.deadlineAt = deadlineAt;
        this.status = RepositoryAnalysisJobStatus.QUEUED;
        this.currentStage = RepositoryAnalysisStageType.VALIDATING;
    }

    @PrePersist void created() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updated() { updatedAt = Instant.now(); }

    public void running(RepositoryAnalysisStageType stage) { status = RepositoryAnalysisJobStatus.RUNNING; currentStage = stage; errorMessage = null; }
    public void checkpoint(int total, int analyzed, int skipped, int calls, int input, int output) {
        totalUnits = total; analyzedUnits = analyzed; skippedUnits = skipped; usedCalls = calls;
        usedInputTokens = input; usedOutputTokens = output;
        progress = total == 0 ? 100 : Math.min(100, (analyzed + skipped) * 100 / total);
    }
    public void queuedForResume() { status = RepositoryAnalysisJobStatus.QUEUED; errorMessage = "Resuming from validated checkpoints after restart"; }
    public void unitsCreated(int total, int skipped) { totalUnits = total; skippedUnits = skipped; updateProgress(); }
    public void consumeCall(int inputTokens) { usedCalls++; usedInputTokens += inputTokens; }
    public void consumeOutput(int outputTokens) { usedOutputTokens += outputTokens; }
    public void unitCompleted(int outputTokens) { analyzedUnits++; usedOutputTokens += outputTokens; updateProgress(); }
    public void unitSkipped() { skippedUnits++; updateProgress(); }
    private void updateProgress() { progress = totalUnits == 0 ? 0 : Math.min(99, (analyzedUnits + skippedUnits) * 100 / totalUnits); }
    public void complete(boolean partial, String message) { status = partial ? RepositoryAnalysisJobStatus.PARTIAL : RepositoryAnalysisJobStatus.COMPLETED; currentStage = RepositoryAnalysisStageType.FINALIZING; progress = 100; errorMessage = bounded(message); }
    public void fail(String message) { status = RepositoryAnalysisJobStatus.FAILED; errorMessage = bounded(message); }
    public void interrupted(String message) { status = RepositoryAnalysisJobStatus.INTERRUPTED; errorMessage = bounded(message); }
    public void requestCancel() { cancelRequested = true; if (status == RepositoryAnalysisJobStatus.QUEUED || status == RepositoryAnalysisJobStatus.RUNNING) status = RepositoryAnalysisJobStatus.CANCELLED; }
    public void cancelled() { cancelRequested = true; status = RepositoryAnalysisJobStatus.CANCELLED; errorMessage = null; }
    private String bounded(String value) { if (value == null || value.isBlank()) return null; return value.substring(0, Math.min(512, value.length())); }

    public Long getId() { return id; } public User getUser() { return user; } public RepositorySnapshot getSnapshot() { return snapshot; }
    public RepositoryAnalysisJobStatus getStatus() { return status; } public RepositoryAnalysisStageType getCurrentStage() { return currentStage; }
    public String getSnapshotHash() { return snapshotHash; } public String getProviderName() { return providerName; }
    public String getProfileId() { return profileId; } public String getModelName() { return modelName; }
    public String getPromptVersion() { return promptVersion; } public String getParserVersion() { return parserVersion; }
    public String getSchemaVersion() { return schemaVersion; } public int getProgress() { return progress; }
    public int getTotalUnits() { return totalUnits; } public int getAnalyzedUnits() { return analyzedUnits; }
    public int getSkippedUnits() { return skippedUnits; } public int getUsedCalls() { return usedCalls; }
    public int getUsedInputTokens() { return usedInputTokens; } public int getUsedOutputTokens() { return usedOutputTokens; }
    public boolean isCancelRequested() { return cancelRequested; } public String getErrorMessage() { return errorMessage; }
    public Instant getDeadlineAt() { return deadlineAt; } public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
