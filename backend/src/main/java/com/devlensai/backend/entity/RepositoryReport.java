package com.devlensai.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repository_reports")
public class RepositoryReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false, unique = true) private RepositoryAnalysisJob job;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "eligible_files", nullable = false) private int eligibleFiles;
    @Column(name = "reviewed_files", nullable = false) private int reviewedFiles;
    @Column(name = "skipped_files", nullable = false) private int skippedFiles;
    @Column(name = "unsupported_files", nullable = false) private int unsupportedFiles;
    @Column(name = "incomplete_passes", nullable = false) private int incompletePasses;
    @Column(name = "partial_coverage", nullable = false) private boolean partialCoverage;
    @Column(name = "coverage_label", nullable = false, length = 512) private String coverageLabel;
    @ElementCollection @CollectionTable(name = "repository_report_module_coverage", joinColumns = @JoinColumn(name = "report_id"))
    @OrderColumn(name = "module_order") private List<RepositoryModuleCoverage> moduleCoverage = new ArrayList<>();
    @Column(name = "used_calls", nullable = false) private int usedCalls;
    @Column(name = "used_input_tokens", nullable = false) private int usedInputTokens;
    @Column(name = "used_output_tokens", nullable = false) private int usedOutputTokens;
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("severity DESC, primaryPath ASC, primaryStartLine ASC, stableId ASC")
    private List<RepositoryFinding> findings = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected RepositoryReport() { }
    public RepositoryReport(RepositoryAnalysisJob job, String status, int eligibleFiles, int reviewedFiles,
            int skippedFiles, int unsupportedFiles, int incompletePasses, boolean partialCoverage,
            String coverageLabel, List<RepositoryModuleCoverage> moduleCoverage,
            int usedCalls, int usedInputTokens, int usedOutputTokens) {
        this.job = job; this.status = status; this.eligibleFiles = eligibleFiles; this.reviewedFiles = reviewedFiles;
        this.skippedFiles = skippedFiles; this.unsupportedFiles = unsupportedFiles; this.incompletePasses = incompletePasses;
        this.partialCoverage = partialCoverage; this.coverageLabel = coverageLabel; this.moduleCoverage = List.copyOf(moduleCoverage); this.usedCalls = usedCalls;
        this.usedInputTokens = usedInputTokens; this.usedOutputTokens = usedOutputTokens;
    }
    @PrePersist void created() { createdAt = Instant.now(); }
    public void addFinding(RepositoryFinding finding) { findings.add(finding); }
    public RepositoryAnalysisJob getJob() { return job; } public String getStatus() { return status; }
    public int getEligibleFiles() { return eligibleFiles; } public int getReviewedFiles() { return reviewedFiles; }
    public int getSkippedFiles() { return skippedFiles; } public int getUnsupportedFiles() { return unsupportedFiles; }
    public int getIncompletePasses() { return incompletePasses; } public boolean isPartialCoverage() { return partialCoverage; }
    public String getCoverageLabel() { return coverageLabel; } public int getUsedCalls() { return usedCalls; }
    public List<RepositoryModuleCoverage> getModuleCoverage() { return List.copyOf(moduleCoverage); }
    public int getUsedInputTokens() { return usedInputTokens; } public int getUsedOutputTokens() { return usedOutputTokens; }
    public List<RepositoryFinding> getFindings() { return List.copyOf(findings); }
}
