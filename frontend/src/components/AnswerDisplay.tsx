import React from 'react';
import { QuestionQueryResponse } from '../types';
import { ResolutionStatusBadge } from './ResolutionStatusBadge';
import { EvidencePanel } from './EvidencePanel';
import { ConflictComparison } from './ConflictComparison';
import { AgentTraceView } from './AgentTraceView';

interface Props {
  response: QuestionQueryResponse;
}

export const AnswerDisplay: React.FC<Props> = ({ response }) => {
  const isAbstained = response.status === 'ABSTAINED';
  const isConflict = response.resolution_status === 'CONFLICT';
  const isInsufficient = response.resolution_status === 'INSUFFICIENT_EVIDENCE';

  return (
    <div style={{ marginTop: '2rem' }}>
      {/* Prominent Red Mock Mode Banner whenever is_mock is true */}
      {response.is_mock && (
        <div style={{
          background: 'rgba(239, 68, 68, 0.15)',
          border: '2px solid #ef4444',
          borderRadius: '8px',
          padding: '0.85rem 1.25rem',
          marginBottom: '1.25rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: '0.88rem',
          color: '#f87171'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <span style={{ fontSize: '1.2rem' }}>🚨</span>
            <div>
              <strong style={{ color: '#ef4444', letterSpacing: '0.05em' }}>DEMO / MOCK DATA</strong>
              <span style={{ marginLeft: '0.5rem', color: 'var(--text-secondary)' }}>
                — This response was generated from synthetic offline mock fixtures. It is NOT live AI pipeline output.
              </span>
            </div>
          </div>
          <span style={{
            background: '#ef4444',
            color: '#fff',
            padding: '0.2rem 0.55rem',
            borderRadius: '4px',
            fontSize: '0.72rem',
            fontWeight: 800,
            letterSpacing: '0.05em',
            whiteSpace: 'nowrap'
          }}>
            DEMO / MOCK DATA
          </span>
        </div>
      )}

      {/* Main Answer / Resolution Card */}
      <div className="card" style={{
        padding: '1.75rem',
        marginBottom: '1.5rem',
        borderTop: isConflict
          ? '4px solid #ef4444'
          : isInsufficient
          ? '4px solid #64748b'
          : '4px solid #10b981'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem', marginBottom: '1.25rem' }}>
          <div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginBottom: '0.25rem', fontFamily: 'var(--font-mono, monospace)' }}>
              EVALUATED QUESTION
            </div>
            <h3 style={{ margin: 0, fontSize: '1.25rem', color: 'var(--text-primary)', lineHeight: 1.4 }}>
              "{response.question}"
            </h3>
          </div>

          <ResolutionStatusBadge status={response.resolution_status} size="lg" />
        </div>

        {/* Answer Content or Abstention */}
        {isAbstained ? (
          <div style={{
            background: isConflict ? 'rgba(239, 68, 68, 0.1)' : 'rgba(100, 116, 139, 0.12)',
            border: `1.5px solid ${isConflict ? '#ef4444' : '#64748b'}`,
            borderRadius: '8px',
            padding: '1.25rem',
            marginBottom: '1rem'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
              <span style={{ fontSize: '1.2rem' }}>{isConflict ? '🛑' : 'ℹ️'}</span>
              <strong style={{ color: isConflict ? '#f87171' : '#cbd5e1', fontSize: '1rem' }}>
                {isConflict ? 'TruthGuard Explicit Abstention (Preserved Conflict)' : 'TruthGuard Explicit Abstention (Insufficient Evidence)'}
              </strong>
            </div>
            <p style={{ margin: 0, color: 'var(--text-primary)', fontSize: '0.92rem', lineHeight: 1.5 }}>
              {response.explanation}
            </p>
          </div>
        ) : (
          <div style={{
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-color)',
            borderRadius: '8px',
            padding: '1.25rem',
            marginBottom: '1rem'
          }}>
            <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '0.4rem', textTransform: 'uppercase' }}>
              Verified Grounded Answer
            </div>
            <div style={{ fontSize: '1rem', color: 'var(--text-primary)', lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
              {response.final_answer}
            </div>
          </div>
        )}

        {/* Rationale & Decision Note */}
        <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)', display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem' }}>
          <div><strong>Decision Rationale:</strong> {response.explanation}</div>
          <div>Query ID: <code style={{ fontSize: '0.75rem' }}>{response.query_id}</code></div>
        </div>
      </div>

      {/* Side-by-side conflict comparison (if conflicts detected) */}
      {response.conflicting_positions && response.conflicting_positions.length > 0 && (
        <ConflictComparison
          positions={response.conflicting_positions}
          resolutionStatus={response.resolution_status}
          explanation={response.explanation}
        />
      )}

      {/* Cited Evidence Panel */}
      <EvidencePanel evidence={response.evidence_references} />

      {/* Expandable Agent Trace */}
      {response.stage_traces && response.stage_traces.length > 0 && (
        <AgentTraceView
          traces={response.stage_traces}
          rejectedClaims={response.rejected_claims || []}
          uncertaintyNotes={response.uncertainty_notes || []}
          totalDurationMs={response.total_duration_ms}
          correlationId={response.correlation_id}
        />
      )}
    </div>
  );
};
