-- ============================================================================
-- TruthGuard Migration V2: Query Audit Record and Processing Stage Trace Tables
-- ============================================================================

CREATE TABLE IF NOT EXISTS queries (
    id UUID PRIMARY KEY,
    correlation_id VARCHAR(100) NOT NULL,
    question TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    resolution_status VARCHAR(50),
    final_answer TEXT,
    explanation TEXT,
    total_duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS query_stage_traces (
    id UUID PRIMARY KEY,
    query_id UUID NOT NULL,
    stage_name VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    duration_ms BIGINT,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_stage_query FOREIGN KEY (query_id) REFERENCES queries(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_queries_correlation_id ON queries(correlation_id);
CREATE INDEX IF NOT EXISTS idx_queries_created_at ON queries(created_at);
CREATE INDEX IF NOT EXISTS idx_stage_traces_query_id ON query_stage_traces(query_id);
