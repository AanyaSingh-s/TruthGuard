import React, { useState } from 'react';
import { QueryStageTrace, RejectedClaim } from '../types';

interface Props {
  traces: QueryStageTrace[];
  rejectedClaims: RejectedClaim[];
  uncertaintyNotes: string[];
  totalDurationMs: number;
  correlationId: string;
}

export const AgentTraceView: React.FC<Props> = ({
  traces,
  rejectedClaims,
  uncertaintyNotes,
  totalDurationMs,
  correlationId
}) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="card" style={{ padding: '1.25rem', marginBottom: '1.5rem' }}>
      <div
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          cursor: 'pointer',
          userSelect: 'none'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
          <span style={{ fontSize: '1.15rem' }}>🔍</span>
          <h4 style={{ margin: 0, fontSize: '1rem', color: 'var(--text-primary)' }}>
            Expandable Agent Execution Trace ({traces.length} Stages)
          </h4>
          <span style={{
            fontSize: '0.75rem',
            fontFamily: 'var(--font-mono, monospace)',
            background: 'var(--bg-secondary)',
            padding: '0.15rem 0.5rem',
            borderRadius: '4px',
            color: 'var(--text-muted)'
          }}>
            {totalDurationMs} ms total
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono, monospace)' }}>
            Correlation: {correlationId}
          </span>
          <span style={{ fontSize: '1rem', transform: expanded ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}>
            ▼
          </span>
        </div>
      </div>

      {expanded && (
        <div style={{ marginTop: '1.25rem', paddingTop: '1rem', borderTop: '1px solid var(--border-color)' }}>
          {/* Stage Timeline */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginBottom: '1.25rem' }}>
            {traces.map((trace, idx) => (
              <div
                key={trace.id || idx}
                style={{
                  background: 'var(--bg-secondary)',
                  border: '1px solid var(--border-color)',
                  borderRadius: '6px',
                  padding: '0.75rem 1rem',
                  display: 'flex',
                  alignItems: 'flex-start',
                  justifyContent: 'space-between',
                  gap: '1rem'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem' }}>
                  <span style={{
                    width: '22px',
                    height: '22px',
                    borderRadius: '50%',
                    background: trace.status === 'SUCCESS' ? 'rgba(16, 185, 129, 0.2)' : 'rgba(239, 68, 68, 0.2)',
                    color: trace.status === 'SUCCESS' ? '#34d399' : '#f87171',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    marginTop: '2px'
                  }}>
                    {idx + 1}
                  </span>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.2rem' }}>
                      <strong style={{ fontSize: '0.875rem', color: 'var(--text-primary)', fontFamily: 'var(--font-mono, monospace)' }}>
                        {trace.stage_name}
                      </strong>
                      <span className={`badge ${trace.status === 'SUCCESS' ? 'badge-success' : 'badge-error'}`} style={{ fontSize: '0.65rem' }}>
                        {trace.status}
                      </span>
                    </div>
                    {trace.details && (
                      <p style={{ margin: 0, fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                        {trace.details}
                      </p>
                    )}
                  </div>
                </div>

                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono, monospace)', whiteSpace: 'nowrap' }}>
                  {trace.duration_ms} ms
                </div>
              </div>
            ))}
          </div>

          {/* Rejected Claims Audit (if any) */}
          {rejectedClaims.length > 0 && (
            <div style={{
              background: 'rgba(239, 68, 68, 0.08)',
              border: '1px solid #ef4444',
              borderRadius: '6px',
              padding: '0.85rem 1rem',
              marginBottom: '1rem'
            }}>
              <div style={{ fontWeight: 600, color: '#f87171', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
                🛡️ Rejected / Ungrounded Claims Audit ({rejectedClaims.length}):
              </div>
              <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                {rejectedClaims.map((rc, i) => (
                  <li key={i} style={{ marginBottom: '0.35rem' }}>
                    <strong>Claim:</strong> "{rc.claim_text}" — <span style={{ color: '#f87171' }}>Reason: {rc.rejection_reason}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Uncertainty Notes */}
          {uncertaintyNotes.length > 0 && (
            <div style={{
              background: 'rgba(245, 158, 11, 0.08)',
              border: '1px solid #f59e0b',
              borderRadius: '6px',
              padding: '0.85rem 1rem'
            }}>
              <div style={{ fontWeight: 600, color: '#fbbf24', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
                ⚠️ Noted Uncertainty & Epistemic Caveats:
              </div>
              <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                {uncertaintyNotes.map((note, i) => (
                  <li key={i} style={{ marginBottom: '0.2rem' }}>{note}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
