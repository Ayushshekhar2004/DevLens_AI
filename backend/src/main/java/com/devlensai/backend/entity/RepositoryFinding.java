package com.devlensai.backend.entity;

import com.devlensai.backend.dto.RepositoryEvidenceReference;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repository_findings", uniqueConstraints =
        @UniqueConstraint(name = "uk_repository_finding_report_stable", columnNames = {"report_id", "stable_id"}))
public class RepositoryFinding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "report_id", nullable = false) private RepositoryReport report;
    @Column(name = "stable_id", nullable = false, length = 64) private String stableId;
    @Column(nullable = false, length = 64) private String category;
    @Column(nullable = false, length = 16) private String severity;
    @Column(nullable = false, length = 512) private String confidence;
    @Column(nullable = false, length = 2048) private String claim;
    @Column(nullable = false, columnDefinition = "text") private String rationale;
    @Column(name = "primary_path", nullable = false, length = 1024) private String primaryPath;
    @Column(name = "primary_start_line", nullable = false) private int primaryStartLine;
    @Column(name = "primary_end_line", nullable = false) private int primaryEndLine;
    @ElementCollection @CollectionTable(name = "repository_finding_evidence", joinColumns = @JoinColumn(name = "finding_id"))
    @OrderColumn(name = "evidence_order") private List<RepositoryFindingEvidence> relatedEvidence = new ArrayList<>();
    @Column(name = "suggested_remediation", nullable = false, columnDefinition = "text") private String suggestedRemediation;
    @Column(nullable = false, length = 16) private String provenance;
    @Column(name = "snapshot_hash", nullable = false, length = 64) private String snapshotHash;
    @Column(name = "model_name", nullable = false, length = 128) private String modelName;

    protected RepositoryFinding() { }
    public RepositoryFinding(RepositoryReport report, String stableId, String category, String severity,
            String confidence, String claim, String rationale, RepositoryEvidenceReference primary,
            List<RepositoryEvidenceReference> related, String remediation, String provenance,
            String snapshotHash, String modelName) {
        this.report = report; this.stableId = stableId; this.category = category; this.severity = severity;
        this.confidence = bounded(confidence, 512); this.claim = bounded(claim, 2048);
        this.rationale = bounded(rationale, 8192); this.primaryPath = primary.relativePath();
        this.primaryStartLine = primary.startLine(); this.primaryEndLine = primary.endLine();
        this.relatedEvidence = related.stream().map(RepositoryFindingEvidence::new).toList();
        this.suggestedRemediation = bounded(remediation, 8192); this.provenance = provenance;
        this.snapshotHash = snapshotHash; this.modelName = modelName;
    }
    private String bounded(String value, int limit) {
        String safe = value == null ? "" : value;
        return safe.substring(0, Math.min(limit, safe.length()));
    }
    public String getStableId() { return stableId; } public String getCategory() { return category; }
    public String getSeverity() { return severity; } public String getConfidence() { return confidence; }
    public String getClaim() { return claim; } public String getRationale() { return rationale; }
    public RepositoryEvidenceReference getPrimaryLocation() { return new RepositoryEvidenceReference(primaryPath, primaryStartLine, primaryEndLine); }
    public List<RepositoryEvidenceReference> getRelatedEvidence() { return relatedEvidence.stream().map(RepositoryFindingEvidence::toReference).toList(); }
    public String getSuggestedRemediation() { return suggestedRemediation; } public String getProvenance() { return provenance; }
    public String getSnapshotHash() { return snapshotHash; } public String getModelName() { return modelName; }
}
