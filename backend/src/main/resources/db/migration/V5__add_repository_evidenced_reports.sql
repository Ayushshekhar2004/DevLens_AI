CREATE TABLE IF NOT EXISTS repository_reports (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL UNIQUE REFERENCES repository_analysis_jobs(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    eligible_files INTEGER NOT NULL,
    reviewed_files INTEGER NOT NULL,
    skipped_files INTEGER NOT NULL,
    unsupported_files INTEGER NOT NULL,
    incomplete_passes INTEGER NOT NULL,
    partial_coverage BOOLEAN NOT NULL,
    coverage_label VARCHAR(512) NOT NULL,
    used_calls INTEGER NOT NULL,
    used_input_tokens INTEGER NOT NULL,
    used_output_tokens INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS repository_findings (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES repository_reports(id) ON DELETE CASCADE,
    stable_id VARCHAR(64) NOT NULL,
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    confidence VARCHAR(512) NOT NULL,
    claim VARCHAR(2048) NOT NULL,
    rationale TEXT NOT NULL,
    primary_path VARCHAR(1024) NOT NULL,
    primary_start_line INTEGER NOT NULL,
    primary_end_line INTEGER NOT NULL,
    suggested_remediation TEXT NOT NULL,
    provenance VARCHAR(16) NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    CONSTRAINT uk_repository_finding_report_stable UNIQUE(report_id, stable_id)
);
CREATE TABLE IF NOT EXISTS repository_report_module_coverage (
    report_id BIGINT NOT NULL REFERENCES repository_reports(id) ON DELETE CASCADE,
    module_order INTEGER NOT NULL,
    module_name VARCHAR(512) NOT NULL,
    eligible_files INTEGER NOT NULL,
    reviewed_files INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    PRIMARY KEY(report_id, module_order)
);
CREATE TABLE IF NOT EXISTS repository_finding_evidence (
    finding_id BIGINT NOT NULL REFERENCES repository_findings(id) ON DELETE CASCADE,
    evidence_order INTEGER NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    PRIMARY KEY(finding_id, evidence_order)
);
CREATE INDEX IF NOT EXISTS idx_repository_report_job ON repository_reports(job_id);
CREATE INDEX IF NOT EXISTS idx_repository_finding_report ON repository_findings(report_id);
