package com.devlensai.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "repository_analysis_units", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "chunk_id"}),
        indexes = @Index(name = "idx_repo_analysis_units_job_status", columnList = "job_id,status"))
public class RepositoryAnalysisUnit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false) private RepositoryAnalysisJob job;
    @Column(name = "chunk_id", nullable = false, length = 64) private String chunkId;
    @Column(name = "relative_path", nullable = false, length = 1024) private String relativePath;
    @Column(name = "file_hash", nullable = false, length = 64) private String fileHash;
    @Column(name = "start_line", nullable = false) private int startLine;
    @Column(name = "end_line", nullable = false) private int endLine;
    @Column(name = "sequence_number", nullable = false) private int sequence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private RepositoryAnalysisUnitStatus status;
    @Column(name = "estimated_input_tokens", nullable = false) private int estimatedInputTokens;
    @Column(name = "estimated_output_tokens", nullable = false) private int estimatedOutputTokens;
    @Column(name = "result_summary", columnDefinition = "text") private String resultSummary;
    @Column(name = "error_message", length = 512) private String errorMessage;

    protected RepositoryAnalysisUnit() { }
    public RepositoryAnalysisUnit(RepositoryAnalysisJob job, String chunkId, String relativePath, String fileHash,
            int startLine, int endLine, int sequence, int estimatedInputTokens) {
        this.job = job; this.chunkId = chunkId; this.relativePath = relativePath; this.fileHash = fileHash;
        this.startLine = startLine; this.endLine = endLine; this.sequence = sequence;
        this.estimatedInputTokens = estimatedInputTokens; this.status = RepositoryAnalysisUnitStatus.PENDING;
    }
    public void running() { status = RepositoryAnalysisUnitStatus.RUNNING; errorMessage = null; }
    public void completed(String summary, int outputTokens) { status = RepositoryAnalysisUnitStatus.COMPLETED; resultSummary = summary; estimatedOutputTokens = outputTokens; errorMessage = null; }
    public void skipped(String reason) { status = RepositoryAnalysisUnitStatus.SKIPPED; errorMessage = bounded(reason); }
    public void resetPending() { if (status == RepositoryAnalysisUnitStatus.RUNNING) status = RepositoryAnalysisUnitStatus.PENDING; }
    public void failed(String reason) { status = RepositoryAnalysisUnitStatus.FAILED; errorMessage = bounded(reason); }
    public void cancelled() { status = RepositoryAnalysisUnitStatus.CANCELLED; errorMessage = null; }
    private String bounded(String value) { String safe = value == null ? "Repository analysis unit failed" : value; return safe.substring(0, Math.min(512, safe.length())); }
    public Long getId() { return id; } public RepositoryAnalysisJob getJob() { return job; }
    public String getChunkId() { return chunkId; } public String getRelativePath() { return relativePath; }
    public String getFileHash() { return fileHash; } public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; } public int getSequence() { return sequence; }
    public RepositoryAnalysisUnitStatus getStatus() { return status; } public int getEstimatedInputTokens() { return estimatedInputTokens; }
    public int getEstimatedOutputTokens() { return estimatedOutputTokens; } public String getResultSummary() { return resultSummary; }
    public String getErrorMessage() { return errorMessage; }
}
