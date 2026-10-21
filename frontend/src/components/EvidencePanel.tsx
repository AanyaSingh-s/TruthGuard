import React from 'react';
import { EvidenceReference } from '../types';

interface Props {
  evidence: EvidenceReference[];
}

export const EvidencePanel: React.FC<Props> = ({ evidence }) => {
  if (!evidence || evidence.length === 0) {
    return (
      <div className="card" style={{ padding: '1.25rem', marginBottom: '1.5rem', color: 'var(--text-muted)', fontSize: '0.9rem', textAlign: 'center' }}>
        No supporting evidence passages were accepted or cited for this response.
      </div>
    );
  }

  return (
    <div className="card" style={{ padding: '1.5rem', marginBottom: '1.5rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontSize: '1.2rem' }}>📜</span>
          <h4 style={{ margin: 0, fontSize: '1.1rem', color: 'var(--text-primary)' }}>
            Cited Evidence & Provenance ({evidence.length})
          </h4>
        </div>
        <span className="badge badge-success">Grounded Citations</span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {evidence.map((ref, idx) => (
          <div
            key={idx}
            style={{
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border-color)',
              borderRadius: '8px',
              padding: '1rem',
              borderLeft: '3px solid var(--accent-emerald, #10b981)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.5rem' }}>
              <div>
                <span style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: '0.95rem' }}>
                  {ref.source_title || 'Document Excerpt'}
                </span>
                {ref.version_identifier && (
                  <span style={{ color: 'var(--text-muted)', fontSize: '0.82rem', marginLeft: '0.5rem' }}>
                    ({ref.version_identifier})
                  </span>
                )}
                {ref.authority_level && (
                  <span style={{
                    fontSize: '0.7rem',
                    fontWeight: 600,
                    padding: '0.1rem 0.4rem',
                    borderRadius: '4px',
                    background: 'rgba(255, 255, 255, 0.06)',
                    border: '1px solid var(--border-color)',
                    marginLeft: '0.5rem'
                  }}>
                    {ref.authority_level}
                  </span>
                )}
              </div>

              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono, monospace)' }}>
                Chunk ID: {ref.chunk_id ? ref.chunk_id.substring(0, 8) + '...' : 'N/A'}
                {ref.page_number && <span style={{ marginLeft: '0.5rem' }}>Page: {ref.page_number}</span>}
              </div>
            </div>

            <p style={{
              margin: '0 0 0.5rem',
              fontSize: '0.85rem',
              color: 'var(--text-secondary)',
              lineHeight: 1.5,
              background: 'rgba(0, 0, 0, 0.2)',
              padding: '0.6rem 0.75rem',
              borderRadius: '6px'
            }}>
              "{ref.snippet}"
            </p>

            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', gap: '1rem' }}>
              <span>Effective Date: <strong>{ref.effective_date || 'Unspecified'}</strong></span>
              <span>Document ID: <code style={{ fontSize: '0.72rem' }}>{ref.document_id ? ref.document_id.substring(0, 8) + '...' : 'N/A'}</code></span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
