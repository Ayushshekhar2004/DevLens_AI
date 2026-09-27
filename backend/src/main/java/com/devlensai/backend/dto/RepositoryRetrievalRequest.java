package com.devlensai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RepositoryRetrievalRequest(
        @NotBlank @Pattern(regexp = "FILE|SYMBOL|MODULE|TASK") String targetType,
        @NotBlank @Size(max = 1024) String target,
        @NotBlank @Size(max = 512) String purpose,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String snapshotHash) { }
