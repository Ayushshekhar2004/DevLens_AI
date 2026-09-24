CREATE TABLE IF NOT EXISTS repository_summaries (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES repository_analysis_jobs(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    summary_level VARCHAR(16) NOT NULL,
    identity VARCHAR(1024) NOT NULL,
    content_identity VARCHAR(64) NOT NULL,
    cache_key VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    responsibilities TEXT,
    uncertainty TEXT,
    cache_hit BOOLEAN NOT NULL DEFAULT FALSE,
    error_message VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_repo_summary_cache UNIQUE(job_id, cache_key)
);
CREATE INDEX IF NOT EXISTS idx_repo_summary_job_level ON repository_summaries(job_id, summary_level);
CREATE INDEX IF NOT EXISTS idx_repo_summary_owner_cache ON repository_summaries(user_id, cache_key);
CREATE TABLE IF NOT EXISTS repository_summary_symbols (summary_id BIGINT NOT NULL REFERENCES repository_summaries(id) ON DELETE CASCADE, symbol_name VARCHAR(512));
CREATE TABLE IF NOT EXISTS repository_summary_dependencies (summary_id BIGINT NOT NULL REFERENCES repository_summaries(id) ON DELETE CASCADE, dependency_name VARCHAR(512));
CREATE TABLE IF NOT EXISTS repository_summary_evidence (summary_id BIGINT NOT NULL REFERENCES repository_summaries(id) ON DELETE CASCADE, relative_path VARCHAR(1024), start_line INTEGER, end_line INTEGER);
