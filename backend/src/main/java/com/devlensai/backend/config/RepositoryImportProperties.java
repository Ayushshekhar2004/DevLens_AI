package com.devlensai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

@Component
public class RepositoryImportProperties {
    private final Path storageRoot;
    private final Path allowedFolderRoot;
    private final long maxUploadBytes;
    private final long maxExpandedBytes;
    private final long maxFileBytes;
    private final int maxFiles;
    private final int maxPathDepth;
    private final double maxDecompressionRatio;
    private final Duration maxElapsed;

    public RepositoryImportProperties(
            @Value("${app.repository-import.storage-root}") String storageRoot,
            @Value("${app.repository-import.allowed-folder-root}") String allowedFolderRoot,
            @Value("${app.repository-import.max-upload-bytes}") long maxUploadBytes,
            @Value("${app.repository-import.max-expanded-bytes}") long maxExpandedBytes,
            @Value("${app.repository-import.max-file-bytes}") long maxFileBytes,
            @Value("${app.repository-import.max-files}") int maxFiles,
            @Value("${app.repository-import.max-path-depth}") int maxPathDepth,
            @Value("${app.repository-import.max-decompression-ratio}") double maxDecompressionRatio,
            @Value("${app.repository-import.max-elapsed-seconds}") long maxElapsedSeconds
    ) {
        if (maxUploadBytes < 1 || maxExpandedBytes < 1 || maxFileBytes < 1 || maxFiles < 1
                || maxPathDepth < 1 || maxDecompressionRatio < 1 || maxElapsedSeconds < 1
                || maxFileBytes > maxExpandedBytes) {
            throw new IllegalArgumentException("Invalid repository import limits");
        }
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.allowedFolderRoot = Path.of(allowedFolderRoot).toAbsolutePath().normalize();
        this.maxUploadBytes = maxUploadBytes;
        this.maxExpandedBytes = maxExpandedBytes;
        this.maxFileBytes = maxFileBytes;
        this.maxFiles = maxFiles;
        this.maxPathDepth = maxPathDepth;
        this.maxDecompressionRatio = maxDecompressionRatio;
        this.maxElapsed = Duration.ofSeconds(maxElapsedSeconds);
    }

    public Path storageRoot() { return storageRoot; }
    public Path allowedFolderRoot() { return allowedFolderRoot; }
    public long maxUploadBytes() { return maxUploadBytes; }
    public long maxExpandedBytes() { return maxExpandedBytes; }
    public long maxFileBytes() { return maxFileBytes; }
    public int maxFiles() { return maxFiles; }
    public int maxPathDepth() { return maxPathDepth; }
    public double maxDecompressionRatio() { return maxDecompressionRatio; }
    public Duration maxElapsed() { return maxElapsed; }
}
