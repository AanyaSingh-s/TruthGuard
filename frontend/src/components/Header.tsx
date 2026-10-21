import React from 'react';

export const Header: React.FC = () => {
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
          <span className="brand-tag">v0.1.0 Foundation</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            Stack: Spring Boot · React · FastAPI · pgvector
          </span>
        </div>
      </div>
    </header>
  );
};
