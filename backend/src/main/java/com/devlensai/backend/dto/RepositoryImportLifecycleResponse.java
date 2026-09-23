package com.devlensai.backend.dto;

public record RepositoryImportLifecycleResponse(
        Long snapshotId,
        RepositoryJobResponse importJob,
        RepositoryJobResponse scanJob
) { }
