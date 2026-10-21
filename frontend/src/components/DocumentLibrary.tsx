import React, { useState, useEffect } from 'react';
import { getDocuments } from '../api/client';
import { DocumentItem } from '../types';

interface Props {
  refreshTrigger?: number;
}

export const DocumentLibrary: React.FC<Props> = ({ refreshTrigger }) => {
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [expandedDocId, setExpandedDocId] = useState<string | null>(null);

  const fetchDocs = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getDocuments();
      setDocuments(data);
    } catch (err: any) {
      setError(err.message || 'Could not load documents from backend');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDocs();
  }, [refreshTrigger]);

  const filtered = documents.filter(doc =>
    doc.title.toLowerCase().includes(search.toLowerCase()) ||
    doc.sourceType.toLowerCase().includes(search.toLowerCase())
  );

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PROCESSED':
        return <span className="badge badge-success">✓ PROCESSED</span>;
      case 'PROCESSING':
        return <span className="badge badge-warning">⏳ PROCESSING</span>;
      case 'FAILED':
        return <span className="badge badge-error">✕ FAILED</span>;
      default:
        return <span className="badge badge-secondary">{status}</span>;
    }
  };

  const getAuthorityBadge = (auth?: string) => {
    if (!auth) return null;
    const colors: Record<string, string> = {
      REGULATORY: '#ec4899',
      EXECUTIVE: '#8b5cf6',
      OFFICIAL: '#3b82f6',
      WORKING_GROUP: '#10b981',
      DRAFT: '#f59e0b'
    };
    return (
      <span style={{
        fontSize: '0.72rem',
        fontWeight: 600,
        padding: '0.15rem 0.5rem',
        borderRadius: '4px',
        background: 'rgba(255, 255, 255, 0.06)',
        color: colors[auth] || '#94a3b8',
        border: `1px solid ${colors[auth] || '#475569'}`
      }}>
        {auth}
      </span>
    );
  };

  return (
    <div className="card" style={{ padding: '1.5rem', marginBottom: '2.5rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.2rem', color: 'var(--text-primary)' }}>
            📚 Document Library & Version Repository
          </h3>
          <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            Inspect registered document versions, authority tiers, effective dates, and ingestion status.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <input
            type="text"
            className="form-input"
            placeholder="Search by title or type..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ width: '220px', padding: '0.4rem 0.75rem', fontSize: '0.85rem' }}
          />
          <button
            onClick={fetchDocs}
            className="btn btn-secondary"
            disabled={loading}
            style={{ padding: '0.4rem 0.75rem', fontSize: '0.85rem' }}
          >
            {loading ? 'Refreshing...' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {error && (
        <div className="alert alert-error" style={{ marginBottom: '1rem' }}>
          ⚠️ {error} (Is Spring Boot running on port 8080?)
        </div>
      )}

      {loading && documents.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
          <div className="spinner" style={{ margin: '0 auto 0.75rem' }} />
          Loading document corpus...
        </div>
      ) : filtered.length === 0 ? (
        <div style={{
          textAlign: 'center',
          padding: '2.5rem',
          background: 'var(--bg-secondary)',
          borderRadius: '8px',
          color: 'var(--text-muted)',
          fontSize: '0.9rem'
        }}>
          {documents.length === 0
            ? 'No documents registered yet. Use the upload panel above to ingest your first policy document.'
            : 'No documents match your search query.'}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {filtered.map(doc => {
            const isExpanded = expandedDocId === doc.id;
            const latestVersion = doc.versions && doc.versions.length > 0
              ? doc.versions[doc.versions.length - 1]
              : null;

            return (
              <div
                key={doc.id}
                style={{
                  background: 'var(--bg-secondary)',
                  border: '1px solid var(--border-color)',
                  borderRadius: '8px',
                  padding: '1rem',
                  transition: 'border-color 0.2s'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.5rem' }}>
                  <div style={{ flex: 1, minWidth: '240px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                      <span style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: '1rem' }}>
                        {doc.title}
                      </span>
                      <span className="badge badge-secondary" style={{ fontSize: '0.7rem' }}>
                        {doc.sourceType}
                      </span>
                      {latestVersion && getAuthorityBadge(latestVersion.authorityLevel)}
                    </div>
                    {doc.description && (
                      <p style={{ margin: '0 0 0.4rem', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                        {doc.description}
                      </p>
                    )}
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    {latestVersion && (
                      <>
                        <div style={{ textAlign: 'right', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                          <div>Version: <strong style={{ color: 'var(--text-secondary)' }}>{latestVersion.versionIdentifier}</strong></div>
                          <div>Effective: {latestVersion.effectiveDate || 'Unspecified'}</div>
                        </div>
                        {getStatusBadge(latestVersion.processingStatus)}
                      </>
                    )}
                    <button
                      onClick={() => setExpandedDocId(isExpanded ? null : doc.id)}
                      className="btn btn-secondary"
                      style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                    >
                      {isExpanded ? 'Hide Versions' : `Versions (${doc.versions?.length || 0})`}
                    </button>
                  </div>
                </div>

                {isExpanded && doc.versions && doc.versions.length > 0 && (
                  <div style={{
                    marginTop: '1rem',
                    paddingTop: '0.75rem',
                    borderTop: '1px solid var(--border-color)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.5rem'
                  }}>
                    <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>
                      Version History & Provenance:
                    </div>
                    {doc.versions.map(v => (
                      <div
                        key={v.id}
                        style={{
                          background: 'rgba(0, 0, 0, 0.25)',
                          padding: '0.6rem 0.85rem',
                          borderRadius: '6px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          fontSize: '0.8rem',
                          fontFamily: 'var(--font-mono, monospace)'
                        }}
                      >
                        <div>
                          <strong>{v.versionIdentifier}</strong>
                          <span style={{ color: 'var(--text-muted)', marginLeft: '0.75rem' }}>
                            Hash: {v.contentHash.substring(0, 10)}...
                          </span>
                          {v.effectiveDate && (
                            <span style={{ color: '#93c5fd', marginLeft: '0.75rem' }}>
                              Effective: {v.effectiveDate}
                            </span>
                          )}
                          {v.supersedesVersionId && (
                            <span style={{ color: '#f59e0b', marginLeft: '0.75rem' }}>
                              ↳ Supersedes older version
                            </span>
                          )}
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                          {getAuthorityBadge(v.authorityLevel)}
                          {getStatusBadge(v.processingStatus)}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
