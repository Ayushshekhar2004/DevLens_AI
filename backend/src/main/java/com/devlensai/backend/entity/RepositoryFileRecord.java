package com.devlensai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RepositoryFileRecord(
        @Column(name = "relative_path", nullable = false, length = 1024) String relativePath,
        @Column(nullable = false, length = 64) String language,
        @Column(name = "content_hash", nullable = false, length = 64) String contentHash,
        @Column(name = "line_count", nullable = false) int lineCount,
        @Column(name = "parser_status", nullable = false, length = 32) String parserStatus,
        @Column(name = "parser_mode", nullable = false, length = 32) String parserMode,
        @Column(name = "module_root", nullable = false, length = 1024) String moduleRoot,
        @Column(name = "safe_content", columnDefinition = "text") String safeContent
) { public RepositoryFileRecord() { this("", "UNSUPPORTED", "", 0, "UNSUPPORTED", "NONE", "", null); } }
