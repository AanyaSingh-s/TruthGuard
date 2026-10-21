import React, { useEffect, useState } from 'react';

interface Props {
  activeTab: 'QA' | 'DOCS';
  onTabChange: (tab: 'QA' | 'DOCS') => void;
}

export const Header: React.FC<Props> = ({ activeTab, onTabChange }) => {
  const [llmProvider, setLlmProvider] = useState<string | null>(null);
  const [llmModel, setLlmModel] = useState<string | null>(null);

  useEffect(() => {
    fetch('http://localhost:8000/health')
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (data) {
          setLlmProvider(data.llm_provider || null);
          setLlmModel(data.llm_model || null);
        }
      })
      .catch(() => {});
  }, []);

  return (
    <header className="header">
      <div className="header-inner">
        <div className="brand">
          <svg
            className="logo-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            <path d="m9 12 2 2 4-4" />
          </svg>
          <span className="brand-title">TruthGuard</span>
          <span className="brand-tag">v0.2.0 Verification</span>
        </div>

        {/* Tab Navigation */}
        <div style={{ display: 'flex', gap: '0.5rem', background: 'var(--bg-secondary)', padding: '0.25rem', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
          <button
            type="button"
            onClick={() => onTabChange('QA')}
            style={{
              padding: '0.4rem 0.85rem',
              fontSize: '0.85rem',
              fontWeight: 600,
              borderRadius: '6px',
              border: 'none',
              cursor: 'pointer',
              background: activeTab === 'QA' ? 'var(--accent-blue)' : 'transparent',
              color: activeTab === 'QA' ? '#fff' : 'var(--text-muted)'
            }}
          >
            🔎 Question Answering & Verification
          </button>
          <button
            type="button"
            onClick={() => onTabChange('DOCS')}
            style={{
              padding: '0.4rem 0.85rem',
              fontSize: '0.85rem',
              fontWeight: 600,
              borderRadius: '6px',
              border: 'none',
              cursor: 'pointer',
              background: activeTab === 'DOCS' ? 'var(--accent-blue)' : 'transparent',
              color: activeTab === 'DOCS' ? '#fff' : 'var(--text-muted)'
            }}
          >
            📚 Ingestion & Document Library
          </button>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          {llmProvider && (
            <span
              style={{
                fontSize: '0.75rem',
                fontWeight: 600,
                padding: '0.25rem 0.65rem',
                borderRadius: '6px',
                border: `1px solid ${llmProvider === 'mock' ? '#f59e0b' : '#10b981'}`,
                background: llmProvider === 'mock' ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)',
                color: llmProvider === 'mock' ? '#fbbf24' : '#34d399',
                display: 'flex',
                alignItems: 'center',
                gap: '0.35rem'
              }}
              title={`Active Model: ${llmModel || 'unknown'}`}
            >
              <span style={{ fontSize: '0.85rem' }}>{llmProvider === 'mock' ? '🧪' : '🤖'}</span>
              LLM: <strong>{llmProvider.toUpperCase()}</strong>
            </span>
          )}
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Ports: <strong>8080</strong> (Java) · <strong>8000</strong> (Python)
          </span>
        </div>
      </div>
    </header>
  );
};
