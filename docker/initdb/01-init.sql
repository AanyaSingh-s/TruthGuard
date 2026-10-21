-- ============================================================================
-- TruthGuard Initial Database Setup Script
-- ============================================================================
-- Enables vector extension for adversarial QA retrieval & inconsistency detection.

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Verification check
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'vector'
    ) THEN
        RAISE NOTICE 'TruthGuard: pgvector extension is enabled and ready.';
    ELSE
        RAISE WARNING 'TruthGuard: pgvector extension could not be initialized.';
    END IF;
END $$;

-- Schema metadata table for tracking database migrations in later phases
CREATE TABLE IF NOT EXISTS schema_version (
    version VARCHAR(50) PRIMARY KEY,
    description TEXT NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_version (version, description)
VALUES ('0.1.0', 'Initial Day 1 baseline schema with pgvector capability')
ON CONFLICT (version) DO NOTHING;
