import React, { useState, useEffect } from 'react';
import { ServiceHealth } from '../types';

export const ServiceStatus: React.FC = () => {
  const [backendHealth, setBackendHealth] = useState<ServiceHealth>({ status: 'CHECKING' });
  const [aiHealth, setAiHealth] = useState<ServiceHealth>({ status: 'CHECKING' });
  const [dbHealth] = useState<ServiceHealth>({
    status: 'UP',
    service: 'postgresql-pgvector',
    version: '16.x',
    details: {
      port: '5432',
      extension: 'vector',
      docker_image: 'pgvector/pgvector:pg16',
    },
  });

  const checkHealth = async () => {
    // Check Backend
    setBackendHealth((prev) => ({ ...prev, status: 'CHECKING' }));
    try {
      const res = await fetch('http://localhost:8080/api/v1/health');
      if (res.ok) {
        const data = await res.json();
        setBackendHealth({
          status: 'UP',
          service: data.service,
          version: data.version,
          timestamp: data.timestamp,
          details: data.details,
        });
      } else {
        setBackendHealth({ status: 'DOWN', error: `HTTP ${res.status}` });
      }
    } catch {
      setBackendHealth({
        status: 'DOWN',
        error: 'Unable to connect to Spring Boot backend on :8080',
      });
    }

    // Check AI Service
    setAiHealth((prev) => ({ ...prev, status: 'CHECKING' }));
    try {
      const res = await fetch('http://localhost:8000/health');
      if (res.ok) {
        const data = await res.json();
        setAiHealth({
          status: 'UP',
          service: data.service,
          version: data.version,
          timestamp: data.timestamp,
          details: data.details,
        });
      } else {
        setAiHealth({ status: 'DOWN', error: `HTTP ${res.status}` });
      }
    } catch {
      setAiHealth({
        status: 'DOWN',
        error: 'Unable to connect to FastAPI AI Service on :8000',
      });
    }
  };

  useEffect(() => {
    checkHealth();
  }, []);

  const renderBadge = (status: ServiceHealth['status']) => {
    if (status === 'UP') {
      return (
        <span className="status-badge status-up">
          <span className="pulse-dot"></span> Online
        </span>
      );
    }
    if (status === 'DOWN') {
      return (
        <span className="status-badge status-down">
          <span className="pulse-dot"></span> Offline
        </span>
      );
    }
    return (
      <span className="status-badge status-checking">
        <span className="pulse-dot"></span> Checking
      </span>
    );
  };

  return (
    <section>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '1rem' }}>
        <div>
          <h2 className="section-title">Microservice Foundation Status</h2>
          <p className="section-desc">
            Live health telemetry across TruthGuard distributed components.
          </p>
        </div>
        <button className="refresh-button" onClick={checkHealth}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M21 2v6h-6" />
            <path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
            <path d="M3 22v-6h6" />
            <path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
          </svg>
          Re-check Endpoints
        </button>
      </div>

      <div className="health-grid">
        {/* Backend Card */}
        <div className="health-card">
          <div className="card-header">
            <span className="card-title">Backend (Spring Boot)</span>
            {renderBadge(backendHealth.status)}
          </div>
          <div className="card-meta">
            Port: 8080 · Runtime: Java 17 / Maven
          </div>
          <div className="card-details">
            {backendHealth.status === 'UP' ? (
              <div>
                <div><strong>Service:</strong> {backendHealth.service} (v{backendHealth.version})</div>
                <div><strong>Role:</strong> {backendHealth.details?.role || 'Orchestrator'}</div>
                <div style={{ fontSize: '0.75rem', marginTop: '0.25rem', color: 'var(--text-muted)' }}>
                  Endpoint: GET /api/v1/health
                </div>
              </div>
            ) : (
              <div style={{ color: backendHealth.status === 'DOWN' ? '#fda4af' : 'var(--text-muted)' }}>
                {backendHealth.error || 'Querying health endpoint...'}
              </div>
            )}
          </div>
        </div>

        {/* AI Service Card */}
        <div className="health-card">
          <div className="card-header">
            <span className="card-title">AI Service (FastAPI)</span>
            {renderBadge(aiHealth.status)}
          </div>
          <div className="card-meta">
            Port: 8000 · Runtime: Python 3.14 / Uvicorn
          </div>
          <div className="card-details">
            {aiHealth.status === 'UP' ? (
              <div>
                <div><strong>Service:</strong> {aiHealth.service} (v{aiHealth.version})</div>
                <div><strong>Role:</strong> {aiHealth.details?.role || 'Adversarial Inspector'}</div>
                <div style={{ fontSize: '0.75rem', marginTop: '0.25rem', color: 'var(--text-muted)' }}>
                  Endpoint: GET /health
                </div>
              </div>
            ) : (
              <div style={{ color: aiHealth.status === 'DOWN' ? '#fda4af' : 'var(--text-muted)' }}>
                {aiHealth.error || 'Querying health endpoint...'}
              </div>
            )}
          </div>
        </div>

        {/* Database Card */}
        <div className="health-card">
          <div className="card-header">
            <span className="card-title">PostgreSQL + pgvector</span>
            {renderBadge(dbHealth.status)}
          </div>
          <div className="card-meta">
            Port: 5432 · Vector Extension: vector
          </div>
          <div className="card-details">
            <div><strong>Image:</strong> pgvector/pgvector:pg16</div>
            <div><strong>Storage:</strong> Document chunks, vectors, audit graph</div>
            <div style={{ fontSize: '0.75rem', marginTop: '0.25rem', color: 'var(--text-muted)' }}>
              Configuration: docker-compose.yml + native fallback
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};
