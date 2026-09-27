package com.devlensai.backend.entity;

import com.devlensai.backend.dto.RepositoryEvidenceReference;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RepositoryFindingEvidence(
        @Column(name = "relative_path", nullable = false, length = 1024) String relativePath,
        @Column(name = "start_line", nullable = false) int startLine,
        @Column(name = "end_line", nullable = false) int endLine
) {
    public RepositoryFindingEvidence() { this("", 1, 1); }
    public RepositoryFindingEvidence(RepositoryEvidenceReference value) {
        this(value.relativePath(), value.startLine(), value.endLine());
    }
    public RepositoryEvidenceReference toReference() {
        return new RepositoryEvidenceReference(relativePath, startLine, endLine);
    }
}
