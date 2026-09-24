package com.devlensai.backend.entity;

import com.devlensai.backend.dto.RepositoryEvidenceReference;
import com.devlensai.backend.dto.RepositorySummaryResult;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repository_summaries", uniqueConstraints = @UniqueConstraint(name = "uk_repo_summary_cache", columnNames = {"job_id", "cache_key"}),
        indexes = @Index(name = "idx_repo_summary_job_level", columnList = "job_id,summary_level"))
public class RepositorySummary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false) private RepositoryAnalysisJob job;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Enumerated(EnumType.STRING) @Column(name = "summary_level", nullable = false, length = 16) private RepositorySummaryLevel level;
    @Column(nullable = false, length = 1024) private String identity;
    @Column(name = "content_identity", nullable = false, length = 64) private String contentIdentity;
    @Column(name = "cache_key", nullable = false, length = 64) private String cacheKey;
    @Column(nullable = false, length = 16) private String status;
    @Column(columnDefinition = "text") private String responsibilities;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "repository_summary_symbols", joinColumns = @JoinColumn(name = "summary_id")) @Column(name = "symbol_name", length = 512) private List<String> keySymbols = new ArrayList<>();
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "repository_summary_dependencies", joinColumns = @JoinColumn(name = "summary_id")) @Column(name = "dependency_name", length = 512) private List<String> dependencies = new ArrayList<>();
    @Column(columnDefinition = "text") private String uncertainty;
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "repository_summary_evidence", joinColumns = @JoinColumn(name = "summary_id"))
    private List<EvidenceEmbeddable> evidence = new ArrayList<>();
    @Column(name = "cache_hit", nullable = false) private boolean cacheHit;
    @Column(name = "error_message", length = 512) private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected RepositorySummary() { }
    public RepositorySummary(RepositoryAnalysisJob job, User user, RepositorySummaryLevel level, String identity,
            String contentIdentity, String cacheKey, RepositorySummaryResult result, boolean cacheHit) {
        this.job = job; this.user = user; this.level = level; this.identity = identity;
        this.contentIdentity = contentIdentity; this.cacheKey = cacheKey; this.status = "COMPLETED"; this.cacheHit = cacheHit;
        apply(result); this.createdAt = Instant.now();
    }
    public static RepositorySummary failed(RepositoryAnalysisJob job, User user, RepositorySummaryLevel level,
            String identity, String contentIdentity, String cacheKey, String error) {
        RepositorySummary value = new RepositorySummary(); value.job = job; value.user = user; value.level = level;
        value.identity = identity; value.contentIdentity = contentIdentity; value.cacheKey = cacheKey;
        value.status = "FAILED"; value.errorMessage = bounded(error, 512); value.uncertainty = "Summary unavailable";
        value.createdAt = Instant.now(); return value;
    }
    public RepositorySummary copyForJob(RepositoryAnalysisJob target) {
        return new RepositorySummary(target, target.getUser(), level, identity, contentIdentity, cacheKey,
                new RepositorySummaryResult(responsibilities, getKeySymbols(), getDependencies(), uncertainty, getEvidence()), true);
    }
    private void apply(RepositorySummaryResult result) {
        responsibilities = bounded(result.responsibilities(), 16_000); uncertainty = bounded(result.uncertainty(), 4_000);
        keySymbols.addAll(result.keySymbols().stream().limit(100).map(v -> bounded(v, 512)).toList());
        dependencies.addAll(result.dependencies().stream().limit(100).map(v -> bounded(v, 512)).toList());
        evidence.addAll(result.evidence().stream().limit(100).map(EvidenceEmbeddable::from).toList());
    }
    private static String bounded(String value, int limit) { String safe = value == null ? "" : value; return safe.substring(0, Math.min(limit, safe.length())); }
    public Long getId() { return id; } public RepositoryAnalysisJob getJob() { return job; } public User getUser() { return user; }
    public RepositorySummaryLevel getLevel() { return level; } public String getIdentity() { return identity; }
    public String getContentIdentity() { return contentIdentity; } public String getCacheKey() { return cacheKey; }
    public String getStatus() { return status; } public String getResponsibilities() { return responsibilities; }
    public List<String> getKeySymbols() { return List.copyOf(keySymbols); } public List<String> getDependencies() { return List.copyOf(dependencies); }
    public String getUncertainty() { return uncertainty; } public boolean isCacheHit() { return cacheHit; }
    public String getErrorMessage() { return errorMessage; }
    public List<RepositoryEvidenceReference> getEvidence() { return evidence.stream().map(EvidenceEmbeddable::toDto).toList(); }

    @Embeddable
    public record EvidenceEmbeddable(@Column(name = "relative_path", length = 1024) String relativePath,
                                     @Column(name = "start_line") int startLine,
                                     @Column(name = "end_line") int endLine) {
        public EvidenceEmbeddable() { this("", 1, 1); }
        static EvidenceEmbeddable from(RepositoryEvidenceReference value) { return new EvidenceEmbeddable(value.relativePath(), value.startLine(), value.endLine()); }
        RepositoryEvidenceReference toDto() { return new RepositoryEvidenceReference(relativePath, startLine, endLine); }
    }
}
