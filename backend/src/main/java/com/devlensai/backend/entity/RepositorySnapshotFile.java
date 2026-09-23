package com.devlensai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class RepositorySnapshotFile {
    @Column(name = "relative_path", nullable = false, length = 1024)
    private String relativePath;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    protected RepositorySnapshotFile() { }

    public RepositorySnapshotFile(String relativePath, String sha256, long sizeBytes) {
        this.relativePath = relativePath;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }

    public String getRelativePath() { return relativePath; }
    public String getSha256() { return sha256; }
    public long getSizeBytes() { return sizeBytes; }
}
