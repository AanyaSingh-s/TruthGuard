import React from 'react';
import { Header } from './components/Header';
import { ServiceStatus } from './components/ServiceStatus';
import { ArchitectureOverview } from './components/ArchitectureOverview';
import { SampleVerificationDemo } from './components/SampleVerificationDemo';
import './App.css';

export const App: React.FC = () => {
  return (
    <div className="app-container">
      <Header />

      <main className="main-content">
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

        {/* Architecture & Product Pillars */}
        <ArchitectureOverview />

        {/* Adversarial Preview */}
        <SampleVerificationDemo />
      </main>

      {/* Footer */}
      <footer className="footer">
        <div>TruthGuard · Adversarial QA Verification System · Day 1 Project Foundation</div>
        <div style={{ marginTop: '0.25rem', color: 'var(--text-muted)' }}>
          Spring Boot (Port 8080) · Vite + React (Port 3000) · FastAPI (Port 8000) · PostgreSQL / pgvector (Port 5432)
        </div>
      </footer>
    </div>
  );
};

export default App;
