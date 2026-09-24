package com.devlensai.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "repository_analysis_stages", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "stage_type"}))
public class RepositoryAnalysisStage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false) private RepositoryAnalysisJob job;
    @Enumerated(EnumType.STRING) @Column(name = "stage_type", nullable = false, length = 16) private RepositoryAnalysisStageType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private RepositoryAnalysisStageStatus status;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "error_message", length = 512) private String errorMessage;
    protected RepositoryAnalysisStage() { }
    public RepositoryAnalysisStage(RepositoryAnalysisJob job, RepositoryAnalysisStageType type) { this.job = job; this.type = type; this.status = RepositoryAnalysisStageStatus.PENDING; }
    public void running() { if (status != RepositoryAnalysisStageStatus.COMPLETED) { status = RepositoryAnalysisStageStatus.RUNNING; if (startedAt == null) startedAt = Instant.now(); } }
    public void completed() { status = RepositoryAnalysisStageStatus.COMPLETED; if (startedAt == null) startedAt = Instant.now(); completedAt = Instant.now(); errorMessage = null; }
    public void failed(String message) { status = RepositoryAnalysisStageStatus.FAILED; errorMessage = message == null ? null : message.substring(0, Math.min(512, message.length())); }
    public Long getId() { return id; } public RepositoryAnalysisStageType getType() { return type; }
    public RepositoryAnalysisStageStatus getStatus() { return status; } public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; } public String getErrorMessage() { return errorMessage; }
}
