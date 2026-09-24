package com.devlensai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartRepositoryAnalysisRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,31}") String profileId,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}") String model) { }
