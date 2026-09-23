package com.devlensai.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "repository_jobs", indexes = {
        @Index(name = "idx_repository_jobs_owner_created", columnList = "user_id,created_at"),
        @Index(name = "idx_repository_jobs_snapshot_type", columnList = "snapshot_id,job_type")
})
public class RepositoryJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id")
    private RepositorySnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 16)
    private RepositoryJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RepositoryJobStatus status;

    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected RepositoryJob() { }

    public RepositoryJob(User user, RepositorySnapshot snapshot, RepositoryJobType type, String sourceName) {
        this.user = user;
        this.snapshot = snapshot;
        this.type = type;
        this.sourceName = sourceName;
        this.status = RepositoryJobStatus.QUEUED;
    }

    @PrePersist void created() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void updated() { updatedAt = Instant.now(); }

    public void running() { status = RepositoryJobStatus.RUNNING; errorMessage = null; }
    public void completed(RepositorySnapshot snapshot) { this.snapshot = snapshot; status = RepositoryJobStatus.COMPLETED; errorMessage = null; }
    public void failed(String message) { status = RepositoryJobStatus.FAILED; errorMessage = bounded(message); }
    public void requestCancellation() { cancelRequested = true; if (status == RepositoryJobStatus.QUEUED) status = RepositoryJobStatus.CANCELLED; }
    public void cancelled() { cancelRequested = true; status = RepositoryJobStatus.CANCELLED; errorMessage = null; }

    private String bounded(String message) {
        String safe = message == null || message.isBlank() ? "Repository operation failed safely" : message;
        return safe.substring(0, Math.min(512, safe.length()));
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public RepositorySnapshot getSnapshot() { return snapshot; }
    public RepositoryJobType getType() { return type; }
    public RepositoryJobStatus getStatus() { return status; }
    public String getSourceName() { return sourceName; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isCancelRequested() { return cancelRequested; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
