package com.devlensai.backend.dto;

import com.devlensai.backend.entity.RepositoryJob;

import java.time.Instant;

public record RepositoryJobResponse(
        Long id, String type, String status, Long snapshotId, String sourceName,
        String errorMessage, Instant createdAt, Instant updatedAt
) {
    public static RepositoryJobResponse from(RepositoryJob job) {
        return new RepositoryJobResponse(job.getId(), job.getType().name(), job.getStatus().name(),
                job.getSnapshot() == null ? null : job.getSnapshot().getId(), job.getSourceName(),
                job.getErrorMessage(), job.getCreatedAt(), job.getUpdatedAt());
    }
}
