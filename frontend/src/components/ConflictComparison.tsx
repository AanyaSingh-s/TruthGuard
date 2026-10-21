import React from 'react';
import { ConflictingPosition, ResolutionStatus } from '../types';

interface Props {
  positions: ConflictingPosition[];
  resolutionStatus: ResolutionStatus;
  explanation: string;
}

export const ConflictComparison: React.FC<Props> = ({ positions, resolutionStatus, explanation }) => {
  if (!positions || positions.length === 0) {
    return null;
  }

  const isResolved = resolutionStatus === 'ANSWERED_WITH_QUALIFICATION' || resolutionStatus === 'ANSWERED';

  return (
    <div className="card" style={{ padding: '1.5rem', marginBottom: '1.5rem', borderLeft: '4px solid #ef4444' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontSize: '1.3rem' }}>⚔️</span>
          <h4 style={{ margin: 0, fontSize: '1.1rem', color: 'var(--text-primary)' }}>
            Adversarial Conflict Inspection (Side-by-Side)
          </h4>
        </div>
        <span className={`badge ${isResolved ? 'badge-info' : 'badge-error'}`}>
          {isResolved ? 'Deterministically Resolved' : 'Unresolvable Conflict (Preserved)'}
        </span>
      </div>

      <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginBottom: '1.25rem' }}>
        TruthGuard detected conflicting policy assertions in the retrieved evidence set. The system refuses to blend or average contradictory statements:
      </p>

      {/* Side-by-side comparison grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
        gap: '1rem',
        marginBottom: '1.25rem'
      }}>
        {positions.map((pos, idx) => (
          <div
            key={idx}
            style={{
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border-color)',
              borderRadius: '8px',
              padding: '1.25rem',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between'
            }}
          >
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <span style={{
                  fontSize: '0.75rem',
                  fontFamily: 'var(--font-mono, monospace)',
                  background: 'rgba(59, 130, 246, 0.15)',
                  color: '#93c5fd',
                  padding: '0.15rem 0.5rem',
                  borderRadius: '4px'
                }}>
                  POSITION #{idx + 1}
                </span>
                {pos.authority_level && (
                  <span style={{
                    fontSize: '0.72rem',
                    fontWeight: 600,
                    padding: '0.15rem 0.5rem',
                    borderRadius: '4px',
                    background: 'rgba(255, 255, 255, 0.05)',
                    border: '1px solid var(--border-color)'
                  }}>
                    {pos.authority_level}
                  </span>
                )}
              </div>

              <h5 style={{ margin: '0 0 0.5rem', color: 'var(--text-primary)', fontSize: '0.95rem' }}>
                {pos.source_title || 'Untitled Source'} ({pos.version_identifier || 'Unknown Version'})
              </h5>

              <div style={{
                background: 'rgba(0, 0, 0, 0.3)',
                borderLeft: '3px solid #f43f5e',
                padding: '0.75rem',
                borderRadius: '4px',
                fontStyle: 'italic',
                fontSize: '0.85rem',
                color: 'var(--text-secondary)',
                marginBottom: '0.75rem'
              }}>
                "{pos.quotation}"
              </div>

              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                <strong>Assertion:</strong> {pos.assertion}
              </div>
            </div>

            <div style={{
              marginTop: '1rem',
              paddingTop: '0.5rem',
              borderTop: '1px solid var(--border-color)',
              fontSize: '0.78rem',
              color: 'var(--text-muted)',
              display: 'flex',
              justifyContent: 'space-between'
            }}>
              <span>Effective: <strong>{pos.effective_date || 'Unspecified'}</strong></span>
              <span>Uploaded: <strong>{pos.upload_timestamp ? new Date(pos.upload_timestamp).toLocaleDateString() : 'N/A'}</strong></span>
            </div>
          </div>
        ))}
      </div>

      {/* Rationale explanation banner */}
      <div style={{
        background: isResolved ? 'rgba(59, 130, 246, 0.1)' : 'rgba(239, 68, 68, 0.1)',
        border: `1px solid ${isResolved ? '#3b82f6' : '#ef4444'}`,
        borderRadius: '8px',
        padding: '1rem'
      }}>
        <div style={{ fontWeight: 600, color: isResolved ? '#60a5fa' : '#f87171', marginBottom: '0.25rem', fontSize: '0.875rem' }}>
          {isResolved ? 'Deterministic Arbitration Outcome:' : 'Conflict Preservation Rule Enforcement:'}
        </div>
        <div style={{ fontSize: '0.85rem', color: 'var(--text-primary)', lineHeight: 1.5 }}>
          {explanation}
        </div>
      </div>
    </div>
  );
};
