package com.devlensai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "analyses")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "programming_language", nullable = false, length = 32)
    private ProgrammingLanguage programmingLanguage;

    @Column(name = "source_code", nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AnalysisStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Analysis() {
    }

    public Analysis(ProgrammingLanguage programmingLanguage, String sourceCode) {
        this.programmingLanguage = programmingLanguage;
        this.sourceCode = sourceCode;
        this.status = AnalysisStatus.PENDING;
    }

    public void markCompleted() {
        this.status = AnalysisStatus.COMPLETED;
    }

    @PrePersist
    void initializeDefaults() {
        if (status == null) {
            status = AnalysisStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public ProgrammingLanguage getProgrammingLanguage() {
        return programmingLanguage;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
