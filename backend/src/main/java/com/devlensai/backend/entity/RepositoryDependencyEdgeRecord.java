package com.devlensai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RepositoryDependencyEdgeRecord(
        @Column(name = "from_path", nullable = false, length = 1024) String fromPath,
        @Column(name = "target_ref", nullable = false, length = 1024) String target,
        @Column(name = "edge_kind", nullable = false, length = 32) String kind,
        @Column(name = "resolution_status", nullable = false, length = 32) String resolutionStatus
) { public RepositoryDependencyEdgeRecord() { this("", "", "IMPORT", "UNRESOLVED"); } }
