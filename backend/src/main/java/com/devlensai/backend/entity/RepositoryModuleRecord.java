package com.devlensai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RepositoryModuleRecord(
        @Column(name = "module_name", nullable = false, length = 255) String name,
        @Column(name = "root_path", nullable = false, length = 1024) String rootPath,
        @Column(name = "module_type", nullable = false, length = 64) String type,
        @Column(name = "manifest_path", length = 1024) String manifestPath
) { public RepositoryModuleRecord() { this("", "", "UNKNOWN", null); } }
