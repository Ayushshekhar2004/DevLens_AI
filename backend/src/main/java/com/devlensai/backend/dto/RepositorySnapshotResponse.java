package com.devlensai.backend.dto;

import com.devlensai.backend.entity.RepositorySnapshot;

import java.time.Instant;
import java.util.List;

public record RepositorySnapshotResponse(
        Long id,
        String sourceName,
        int fileCount,
        long totalBytes,
        Instant createdAt,
        List<FileResponse> files
) {
    public static RepositorySnapshotResponse from(RepositorySnapshot snapshot) {
        return new RepositorySnapshotResponse(
                snapshot.getId(), snapshot.getSourceName(), snapshot.getFiles().size(),
                snapshot.getTotalBytes(), snapshot.getCreatedAt(),
                snapshot.getFiles().stream().map(file -> new FileResponse(
                        file.getRelativePath(), file.getSha256(), file.getSizeBytes())).toList()
        );
    }

    public record FileResponse(String relativePath, String sha256, long sizeBytes) { }
}
