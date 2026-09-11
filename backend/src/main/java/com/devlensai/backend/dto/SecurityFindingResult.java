package com.devlensai.backend.dto;

import com.devlensai.backend.entity.SecuritySeverity;

public record SecurityFindingResult(
        String title,
        SecuritySeverity severity,
        String explanation,
        String vulnerableLocation,
        String suggestedRemediation,
        String confidenceOrUncertainty
) {
}
