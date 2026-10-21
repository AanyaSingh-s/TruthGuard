import React from 'react';

export const SampleVerificationDemo: React.FC = () => {
  return (
    <section style={{ marginBottom: '3.5rem' }}>
      <h2 className="section-title">Adversarial Inspection Paradigm Preview</h2>
      <p className="section-desc">
        How TruthGuard handles contradictory corpus sources without hallucinating consensus.
      </p>

      <div style={{
        background: 'var(--bg-card)',
        border: '1px solid var(--border-color)',
        borderRadius: '12px',
        padding: '1.5rem',
      }}>
        <div style={{ marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <span style={{
            fontSize: '0.75rem',
            fontFamily: 'var(--font-mono)',
            background: 'rgba(59, 130, 246, 0.2)',
            color: '#93c5fd',
            padding: '0.2rem 0.5rem',
            borderRadius: '4px'
          }}>
            EXAMPLE QUERY
          </span>
          <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
            "What was the release date and architecture standard for Project Nova?"
          </span>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '1rem', marginBottom: '1.25rem' }}>
          {/* Source Document A */}
          <div style={{ background: 'var(--bg-secondary)', padding: '1rem', borderRadius: '8px', borderLeft: '3px solid #3b82f6' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.4rem' }}>
              <span>Doc A: Internal Memo (v1.2)</span>
              <span>Timestamp: 2024-03-15</span>
            </div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              "Project Nova was launched on March 1, 2024 using Monolithic Architecture."
            </p>
          </div>

          {/* Source Document B */}
          <div style={{ background: 'var(--bg-secondary)', padding: '1rem', borderRadius: '8px', borderLeft: '3px solid #f43f5e' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.4rem' }}>
              <span>Doc B: Tech Lead Briefing (v2.0)</span>
              <span>Timestamp: 2024-08-10</span>
            </div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              "Project Nova launched on August 1, 2024, migrating fully to Event-Driven Microservices."
            </p>
          </div>
        </div>

        {/* TruthGuard Result Box */}
        <div style={{
          background: 'rgba(244, 63, 94, 0.08)',
          border: '1px solid rgba(244, 63, 94, 0.3)',
          borderRadius: '8px',
          padding: '1rem',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#fb7185', fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.5rem' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            Deterministic Conflict Detected — Abstention Triggered
          </div>
          <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
            <strong>Direct Contradiction:</strong> Launch date and architectural topology conflict between Doc A (2024-03-15) and Doc B (2024-08-10).
            <br />
            <strong>TruthGuard Guarantee:</strong> The system refuses to hallucinate a composite blend or choose silently without verified temporal provenance rules.
          </div>
        </div>
      </div>
    </section>
  );
};
