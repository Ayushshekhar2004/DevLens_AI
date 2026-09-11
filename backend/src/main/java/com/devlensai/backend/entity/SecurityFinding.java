package com.devlensai.backend.entity;

import com.devlensai.backend.dto.SecurityFindingResult;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class SecurityFinding {

    @Column(name = "finding_title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private SecuritySeverity severity;

    @Column(name = "finding_explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "vulnerable_location", nullable = false, columnDefinition = "TEXT")
    private String vulnerableLocation;

    @Column(name = "suggested_remediation", nullable = false, columnDefinition = "TEXT")
    private String suggestedRemediation;

    @Column(name = "confidence_or_uncertainty", nullable = false, columnDefinition = "TEXT")
    private String confidenceOrUncertainty;

    protected SecurityFinding() {
    }

    public SecurityFinding(SecurityFindingResult result) {
        this.title = result.title();
        this.severity = result.severity();
        this.explanation = result.explanation();
        this.vulnerableLocation = result.vulnerableLocation();
        this.suggestedRemediation = result.suggestedRemediation();
        this.confidenceOrUncertainty = result.confidenceOrUncertainty();
    }

    public SecurityFindingResult toResult() {
        return new SecurityFindingResult(
                title,
                severity,
                explanation,
                vulnerableLocation,
                suggestedRemediation,
                confidenceOrUncertainty
        );
    }
}
