package com.devlensai.backend.entity;

import com.devlensai.backend.dto.CodeReviewResult;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "analyses",
        indexes = {
                @Index(name = "idx_analyses_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_analyses_user_language", columnList = "user_id, programming_language")
        }
)
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

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

    @Column(columnDefinition = "TEXT")
    private String summary;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "analysis_potential_bugs", joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "bug", nullable = false, columnDefinition = "TEXT")
    private List<String> potentialBugs = new ArrayList<>();

    @Column(name = "time_complexity", columnDefinition = "TEXT")
    private String timeComplexity;

    @Column(name = "space_complexity", columnDefinition = "TEXT")
    private String spaceComplexity;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "analysis_edge_cases", joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "edge_case", nullable = false, columnDefinition = "TEXT")
    private List<String> edgeCases = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "analysis_suggestions", joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "suggestion", nullable = false, columnDefinition = "TEXT")
    private List<String> suggestions = new ArrayList<>();

    @Column(name = "improved_code", columnDefinition = "TEXT")
    private String improvedCode;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "analysis_generated_test_cases", joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "item_order")
    private List<GeneratedTestCase> generatedTestCases = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "analysis_security_findings", joinColumns = @JoinColumn(name = "analysis_id"))
    @OrderColumn(name = "item_order")
    private List<SecurityFinding> securityFindings = new ArrayList<>();

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    protected Analysis() {
    }

    public Analysis(User user, ProgrammingLanguage programmingLanguage, String sourceCode) {
        this.user = user;
        this.programmingLanguage = programmingLanguage;
        this.sourceCode = sourceCode;
        this.status = AnalysisStatus.PENDING;
    }

    public void completeWith(CodeReviewResult result) {
        this.summary = result.summary();
        this.potentialBugs.clear();
        this.potentialBugs.addAll(result.potentialBugs());
        this.timeComplexity = result.timeComplexity();
        this.spaceComplexity = result.spaceComplexity();
        this.edgeCases.clear();
        this.edgeCases.addAll(result.edgeCases());
        this.suggestions.clear();
        this.suggestions.addAll(result.suggestions());
        this.improvedCode = result.improvedCode();
        this.generatedTestCases.clear();
        result.generatedTestCases().stream()
                .map(GeneratedTestCase::new)
                .forEach(this.generatedTestCases::add);
        this.securityFindings.clear();
        result.securityFindings().stream()
                .map(SecurityFinding::new)
                .forEach(this.securityFindings::add);
        this.failureReason = null;
        this.status = AnalysisStatus.COMPLETED;
    }

    public void markFailed(String failureReason) {
        this.failureReason = failureReason;
        this.status = AnalysisStatus.FAILED;
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

    public User getUser() {
        return user;
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

    public CodeReviewResult getResult() {
        if (status != AnalysisStatus.COMPLETED || summary == null) {
            return null;
        }
        return new CodeReviewResult(
                summary,
                potentialBugs,
                timeComplexity,
                spaceComplexity,
                edgeCases,
                suggestions,
                improvedCode,
                generatedTestCases.stream()
                        .map(GeneratedTestCase::toResult)
                        .toList(),
                securityFindings.stream()
                        .map(SecurityFinding::toResult)
                        .toList()
        );
    }

    public String getFailureReason() {
        return failureReason;
    }
}
