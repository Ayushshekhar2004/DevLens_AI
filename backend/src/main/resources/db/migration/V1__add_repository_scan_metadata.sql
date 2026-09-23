CREATE TABLE IF NOT EXISTS repository_scans (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    parser_version VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    stack_summary VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_repository_scans_owner ON repository_scans(user_id, snapshot_id);

CREATE TABLE IF NOT EXISTS repository_scan_modules (
    scan_id BIGINT NOT NULL REFERENCES repository_scans(id) ON DELETE CASCADE,
    module_name VARCHAR(255) NOT NULL,
    root_path VARCHAR(1024) NOT NULL,
    module_type VARCHAR(64) NOT NULL,
    manifest_path VARCHAR(1024)
);

CREATE TABLE IF NOT EXISTS repository_scan_files (
    scan_id BIGINT NOT NULL REFERENCES repository_scans(id) ON DELETE CASCADE,
    relative_path VARCHAR(1024) NOT NULL,
    language VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    line_count INTEGER NOT NULL,
    parser_status VARCHAR(32) NOT NULL,
    parser_mode VARCHAR(32) NOT NULL,
    module_root VARCHAR(1024) NOT NULL,
    safe_content TEXT
);

CREATE INDEX IF NOT EXISTS idx_repository_scan_files_path ON repository_scan_files(scan_id, relative_path);

CREATE TABLE IF NOT EXISTS repository_scan_symbols (
    scan_id BIGINT NOT NULL REFERENCES repository_scans(id) ON DELETE CASCADE,
    file_path VARCHAR(1024) NOT NULL,
    symbol_name VARCHAR(255) NOT NULL,
    symbol_kind VARCHAR(32) NOT NULL,
    line_number INTEGER NOT NULL,
    extraction_mode VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS repository_scan_imports (
    scan_id BIGINT NOT NULL REFERENCES repository_scans(id) ON DELETE CASCADE,
    file_path VARCHAR(1024) NOT NULL,
    specifier VARCHAR(1024) NOT NULL,
    line_number INTEGER NOT NULL,
    resolution_status VARCHAR(32) NOT NULL,
    extraction_mode VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS repository_scan_dependency_edges (
    scan_id BIGINT NOT NULL REFERENCES repository_scans(id) ON DELETE CASCADE,
    from_path VARCHAR(1024) NOT NULL,
    target_ref VARCHAR(1024) NOT NULL,
    edge_kind VARCHAR(32) NOT NULL,
    resolution_status VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS repository_scan_skips (
    scan_id BIGINT NOT NULL REFERENCES repository_scans(id) ON DELETE CASCADE,
    relative_path VARCHAR(1024) NOT NULL,
    skip_reason VARCHAR(64) NOT NULL
);
