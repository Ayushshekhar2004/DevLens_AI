CREATE TABLE IF NOT EXISTS repository_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_id BIGINT,
    job_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    error_message VARCHAR(512),
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_repository_jobs_owner_created ON repository_jobs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_repository_jobs_snapshot_type ON repository_jobs(snapshot_id, job_type);
