package com.devlensai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RepositorySkipRecord(
        @Column(name = "relative_path", nullable = false, length = 1024) String relativePath,
        @Column(name = "skip_reason", nullable = false, length = 64) String reason
) { public RepositorySkipRecord() { this("", "UNSUPPORTED"); } }
