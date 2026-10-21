import React, { useState } from 'react';
import { Header } from './components/Header';
import { ServiceStatus } from './components/ServiceStatus';
import { ArchitectureOverview } from './components/ArchitectureOverview';
import { DocumentUpload } from './components/DocumentUpload';
import { DocumentLibrary } from './components/DocumentLibrary';
import { QuestionPanel } from './components/QuestionPanel';
import './App.css';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'QA' | 'DOCS'>('QA');
  const [docsRefreshCounter, setDocsRefreshCounter] = useState(0);

  const handleUploadSuccess = () => {
    setDocsRefreshCounter(prev => prev + 1);
  };

  return (
    <div className="app-container">
      <Header activeTab={activeTab} onTabChange={setActiveTab} />

      <main className="main-content">
        {activeTab === 'QA' ? (
          <>
            {/* Hero Section */}
            <div className="hero">
              <div className="hero-badge">
                <span>🛡️ TruthGuard System Platform</span>
              </div>
              <h1>
                Adversarial Verification for <br />
                <span className="hero-gradient">Inconsistent Document Corpora</span>
              </h1>
              <p className="hero-subtitle">
                A production-grade question answering system engineered to detect contradictions,
                uncover stale sources, audit provenance, and prevent hallucinated consensus.
              </p>

              <div className="guarantee-banner">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                </svg>
                <span>Core Guarantee: Never silently combine contradictory claims or claim an action succeeded when it has not.</span>
              </div>
            </div>

            {/* Live Service Telemetry */}
            <ServiceStatus />

            {/* Adversarial QA Dashboard */}
            <QuestionPanel />

            {/* Architecture & Pillars */}
            <ArchitectureOverview />
          </>
        ) : (
          <>
            <div style={{ marginBottom: '2rem' }}>
              <h2 className="section-title">Ingestion Pipeline & Document Version Library</h2>
              <p className="section-desc">
                Upload new policies, view processing states, and inspect document versions and effective dates.
              </p>
            </div>

            {/* Document Upload */}
            <DocumentUpload onUploadSuccess={handleUploadSuccess} />

            {/* Document Library */}
            <DocumentLibrary refreshTrigger={docsRefreshCounter} />
          </>
        )}
      </main>

      {/* Footer */}
      <footer className="footer">
        <div>TruthGuard · Adversarial QA Verification System · Day 10 React + TypeScript Dashboard</div>
        <div style={{ marginTop: '0.25rem', color: 'var(--text-muted)' }}>
          Spring Boot (Port 8080) · Vite + React (Port 3000) · FastAPI (Port 8000) · PostgreSQL / pgvector (Port 5432)
        </div>
      </footer>
    </div>
  );
};

export default App;
