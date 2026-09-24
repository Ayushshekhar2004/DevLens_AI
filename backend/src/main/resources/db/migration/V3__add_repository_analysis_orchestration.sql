CREATE TABLE IF NOT EXISTS repository_analysis_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_stage VARCHAR(16) NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    provider_name VARCHAR(32) NOT NULL,
    profile_id VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    parser_version VARCHAR(32) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    total_units INTEGER NOT NULL DEFAULT 0,
    analyzed_units INTEGER NOT NULL DEFAULT 0,
    skipped_units INTEGER NOT NULL DEFAULT 0,
    used_calls INTEGER NOT NULL DEFAULT 0,
    used_input_tokens INTEGER NOT NULL DEFAULT 0,
    used_output_tokens INTEGER NOT NULL DEFAULT 0,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    error_message VARCHAR(512),
    deadline_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_repo_analysis_owner_created ON repository_analysis_jobs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_repo_analysis_snapshot_status ON repository_analysis_jobs(snapshot_id, status);

CREATE TABLE IF NOT EXISTS repository_analysis_stages (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES repository_analysis_jobs(id) ON DELETE CASCADE,
    stage_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(512),
    CONSTRAINT uk_repo_analysis_stage UNIQUE(job_id, stage_type)
);

CREATE TABLE IF NOT EXISTS repository_analysis_units (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES repository_analysis_jobs(id) ON DELETE CASCADE,
    chunk_id VARCHAR(64) NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    sequence_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    estimated_input_tokens INTEGER NOT NULL DEFAULT 0,
    estimated_output_tokens INTEGER NOT NULL DEFAULT 0,
    result_summary TEXT,
    error_message VARCHAR(512),
    CONSTRAINT uk_repo_analysis_chunk UNIQUE(job_id, chunk_id)
);

CREATE INDEX IF NOT EXISTS idx_repo_analysis_units_job_status ON repository_analysis_units(job_id, status);
