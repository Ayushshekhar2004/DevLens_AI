package com.devlensai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record RepositoryScanProperties(
        long maxTextBytes,
        long maxManifestBytes,
        int maxSymbolsPerFile,
        int maxImportsPerFile
) {
    public RepositoryScanProperties(
            @Value("${app.repository-scan.max-text-bytes}") long maxTextBytes,
            @Value("${app.repository-scan.max-manifest-bytes}") long maxManifestBytes,
            @Value("${app.repository-scan.max-symbols-per-file}") int maxSymbolsPerFile,
            @Value("${app.repository-scan.max-imports-per-file}") int maxImportsPerFile) {
        if (maxTextBytes < 1 || maxManifestBytes < 1 || maxSymbolsPerFile < 1 || maxImportsPerFile < 1) {
            throw new IllegalArgumentException("Invalid repository scan limits");
        }
        this.maxTextBytes = maxTextBytes;
        this.maxManifestBytes = maxManifestBytes;
        this.maxSymbolsPerFile = maxSymbolsPerFile;
        this.maxImportsPerFile = maxImportsPerFile;
    }
}
