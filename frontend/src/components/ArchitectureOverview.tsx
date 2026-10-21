import React from 'react';

export const ArchitectureOverview: React.FC = () => {
  const pillars = [
    {
      step: '01',
      title: 'Evidence Retrieval',
      desc: 'Retrieves relevant passages across inconsistent document corpora using dense and sparse semantic indices.',
    },
    {
      step: '02',
      title: 'Candidate Generation',
      desc: 'Drafts candidate answers strictly grounded in retrieved evidence, capturing claim attributions.',
    },
    {
      step: '03',
      title: 'Adversarial Inspection',
      desc: 'Critically inspects candidate claims for logical contradictions, unsupported assertions, and temporal staleness.',
    },
    {
      step: '04',
      title: 'Deterministic Resolution',
      desc: 'Executes explicit, deterministic business rules to arbitrate conflicts rather than letting an LLM guess.',
    },
    {
      step: '05',
      title: 'Explainable Provenance',
      desc: 'Displays the resolved answer, source provenance, conflict graph, and explicit abstention reasoning.',
    },
    {
      step: '06',
      title: 'Zero Silent Compromise',
      desc: 'Never silently combines contradictory claims or claims an action succeeded when it has not.',
    },
  ];

  return (
    <section>
      <h2 className="section-title">Core Product Pillars</h2>
      <p className="section-desc">
        TruthGuard enforces strict epistemological safety against hallucinations and corpus inconsistency.
      </p>

      <div className="pipeline-grid">
        {pillars.map((p) => (
          <div key={p.step} className="pipeline-card">
            <div className="step-num">PILLAR {p.step}</div>
            <div className="pipeline-title">{p.title}</div>
            <div className="pipeline-desc">{p.desc}</div>
          </div>
        ))}
      </div>

      <div className="arch-card">
        <h3 style={{ fontSize: '1.15rem', fontWeight: 600, marginBottom: '0.5rem' }}>
          Service Responsibility Matrix
        </h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          Clean boundary separation between orchestration, AI computation, and presentation.
        </p>

        <table className="arch-table">
          <thead>
            <tr>
              <th>Layer</th>
              <th>Service</th>
              <th>Stack</th>
              <th>Key Responsibilities</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style={{ fontWeight: 600 }}>Frontend</td>
              <td><code>truthguard-ui</code></td>
              <td>React 18, TypeScript, Vite</td>
              <td>Question input, evidence inspection, provenance display, contradiction alerts, abstention visualization.</td>
            </tr>
            <tr>
              <td style={{ fontWeight: 600 }}>Backend</td>
              <td><code>truthguard-backend</code></td>
              <td>Java 17, Spring Boot 3, Maven</td>
              <td>Session orchestration, deterministic rule arbitration, ingestion pipeline, API security, audit logs.</td>
            </tr>
            <tr>
              <td style={{ fontWeight: 600 }}>AI Service</td>
              <td><code>truthguard-ai</code></td>
              <td>Python 3.14, FastAPI, Uvicorn</td>
              <td>Vector embeddings, semantic retrieval, candidate generation, adversarial claim checking.</td>
            </tr>
            <tr>
              <td style={{ fontWeight: 600 }}>Database</td>
              <td><code>truthguard-db</code></td>
              <td>PostgreSQL 16, pgvector</td>
              <td>Document chunk persistence, HNSW vector embeddings, conflict graphs, execution traces.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
};
