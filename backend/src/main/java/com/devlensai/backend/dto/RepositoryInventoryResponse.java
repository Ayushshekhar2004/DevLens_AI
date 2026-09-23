package com.devlensai.backend.dto;

import com.devlensai.backend.entity.RepositoryModuleRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RepositoryInventoryResponse {
    private RepositoryInventoryResponse() { }

    public record Page<T>(List<T> content, int page, int size, long totalElements,
                          int totalPages, boolean first, boolean last) { }

    public record Summary(Long snapshotId, String sourceName, Instant createdAt, long totalBytes,
                          int acceptedFileCount, String importStatus, String scanStatus,
                          String detectedStack, int includedCount, int skippedCount,
                          Map<String, Long> skipReasons, double parserCoverage) { }

    public record Detail(Summary summary, List<RepositoryModuleRecord> modules,
                         Page<RepositoryScanResponse.FileMetadata> files) { }
}
