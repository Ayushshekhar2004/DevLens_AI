package com.devlensai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RepositoryModuleCoverage(
        @Column(name = "module_name", nullable = false, length = 512) String module,
        @Column(name = "eligible_files", nullable = false) int eligibleFiles,
        @Column(name = "reviewed_files", nullable = false) int reviewedFiles,
        @Column(nullable = false, length = 16) String status
) { public RepositoryModuleCoverage() { this("", 0, 0, "SKIPPED"); } }
