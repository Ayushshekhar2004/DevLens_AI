package com.devlensai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RepositoryImportRecord(
        @Column(name = "file_path", nullable = false, length = 1024) String filePath,
        @Column(nullable = false, length = 1024) String specifier,
        @Column(name = "line_number", nullable = false) int line,
        @Column(name = "resolution_status", nullable = false, length = 32) String resolutionStatus,
        @Column(name = "extraction_mode", nullable = false, length = 32) String extractionMode
) { public RepositoryImportRecord() { this("", "", 0, "UNRESOLVED", "HEURISTIC"); } }
