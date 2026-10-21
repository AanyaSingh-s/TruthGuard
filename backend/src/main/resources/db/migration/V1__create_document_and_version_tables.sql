-- ============================================================================
-- TruthGuard Migration V1: Document and Document Version Tables
-- ============================================================================

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS document_versions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    version_identifier VARCHAR(100) NOT NULL,
    upload_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    effective_date DATE,
    authority_level VARCHAR(50),
    content_hash VARCHAR(64) NOT NULL,
    raw_content TEXT,
    processing_status VARCHAR(50) NOT NULL,
    supersedes_version_id UUID,
    superseded_by_id UUID,
    CONSTRAINT fk_version_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_version_supersedes FOREIGN KEY (supersedes_version_id) REFERENCES document_versions(id) ON DELETE SET NULL,
    CONSTRAINT fk_version_superseded_by FOREIGN KEY (superseded_by_id) REFERENCES document_versions(id) ON DELETE SET NULL,
    CONSTRAINT uq_document_version_hash UNIQUE (document_id, content_hash)
);

CREATE INDEX IF NOT EXISTS idx_doc_versions_doc_id ON document_versions(document_id);
CREATE INDEX IF NOT EXISTS idx_doc_versions_upload_time ON document_versions(upload_timestamp);
CREATE INDEX IF NOT EXISTS idx_doc_versions_effective_date ON document_versions(effective_date);
CREATE INDEX IF NOT EXISTS idx_doc_versions_content_hash ON document_versions(content_hash);
