package com.devlensai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RepositorySymbolRecord(
        @Column(name = "file_path", nullable = false, length = 1024) String filePath,
        @Column(name = "symbol_name", nullable = false, length = 255) String name,
        @Column(name = "symbol_kind", nullable = false, length = 32) String kind,
        @Column(name = "line_number", nullable = false) int line,
        @Column(name = "extraction_mode", nullable = false, length = 32) String extractionMode
) { public RepositorySymbolRecord() { this("", "", "UNKNOWN", 0, "HEURISTIC"); } }
