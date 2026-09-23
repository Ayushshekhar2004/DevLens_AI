package com.devlensai.backend.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repository_snapshots")
public class RepositorySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "storage_key", nullable = false, unique = true, length = 36)
    private String storageKey;

    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "repository_snapshot_files", joinColumns = @JoinColumn(name = "snapshot_id"))
    @OrderColumn(name = "file_order")
    private List<RepositorySnapshotFile> files = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RepositorySnapshot() { }

    public RepositorySnapshot(User user, String storageKey, String sourceName,
                              long totalBytes, List<RepositorySnapshotFile> files) {
        this.user = user;
        this.storageKey = storageKey;
        this.sourceName = sourceName;
        this.totalBytes = totalBytes;
        this.files.addAll(files);
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getStorageKey() { return storageKey; }
    public String getSourceName() { return sourceName; }
    public long getTotalBytes() { return totalBytes; }
    public List<RepositorySnapshotFile> getFiles() { return List.copyOf(files); }
    public Instant getCreatedAt() { return createdAt; }
}
