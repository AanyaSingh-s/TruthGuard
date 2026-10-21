import React, { useState } from 'react';
import { askQuestion, getMockResponse } from '../api/client';
import { QuestionQueryResponse } from '../types';
import { AnswerDisplay } from './AnswerDisplay';

interface SampleQuery {
  label: string;
  question: string;
  expectedOutcome: string;
  mockScenario: 'ANSWERED' | 'QUALIFIED' | 'CONFLICT' | 'INSUFFICIENT';
}

const SAMPLE_QUERIES: SampleQuery[] = [
  {
    label: 'Official Security (Consistent)',
    question: 'What TLS encryption standard is required for production database connections?',
    expectedOutcome: 'ANSWERED',
    mockScenario: 'ANSWERED'
  },
  {
    label: 'Explicit Supersession (v2.0 vs v1.0)',
    question: 'What is the required web session idle timeout under corporate access policy?',
    expectedOutcome: 'ANSWERED_WITH_QUALIFICATION',
    mockScenario: 'QUALIFIED'
  },
  {
    label: 'Direct Conflict (Equal Authority)',
    question: 'What is the mandatory web session timeout across company systems?',
    expectedOutcome: 'CONFLICT (ABSTAINED)',
    mockScenario: 'CONFLICT'
  },
  {
    label: 'Insufficient Evidence (Missing Facts)',
    question: 'What is the maximum allowed reimbursement budget for offsite team dinners in Tokyo?',
    expectedOutcome: 'INSUFFICIENT_EVIDENCE',
    mockScenario: 'INSUFFICIENT'
  }
];

export const QuestionPanel: React.FC = () => {
  const [question, setQuestion] = useState('');
  const [mode, setMode] = useState<'LIVE' | 'MOCK'>('LIVE');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeResponse, setActiveResponse] = useState<QuestionQueryResponse | null>(null);

  const handleSubmit = async (e?: React.FormEvent, customQuestion?: string, customMockScenario?: 'ANSWERED' | 'QUALIFIED' | 'CONFLICT' | 'INSUFFICIENT') => {
    if (e) e.preventDefault();
    const queryText = (customQuestion || question).trim();

    if (!queryText) {
      setError('Please enter a question to evaluate.');
      return;
    }

    setLoading(true);
    setError(null);

    if (mode === 'MOCK' || customMockScenario) {
      // Simulate slight network delay for realism
      setTimeout(() => {
        const scenario = customMockScenario || 'ANSWERED';
        const mockRes = getMockResponse(scenario);
        mockRes.question = queryText;
        setActiveResponse(mockRes);
        setLoading(false);
      }, 350);
      return;
    }

    try {
      // Call actual Spring Boot backend (Day 9 integration)
      const res = await askQuestion(queryText);
      setActiveResponse(res);
    } catch (err: any) {
      setError(err.message || 'Error executing QA verification pipeline.');
    } finally {
      setLoading(false);
    }
  };

  const selectSample = (sample: SampleQuery) => {
    setQuestion(sample.question);
    if (mode === 'MOCK') {
      handleSubmit(undefined, sample.question, sample.mockScenario);
    }
  };

  return (
    <div>
      <div className="card" style={{ padding: '1.5rem', marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.2rem', color: 'var(--text-primary)' }}>
              🔎 Adversarial Question Answering & Verification
            </h3>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Evaluate queries across your document corpus with claim extraction, adversarial criticism, and deterministic arbitration.
            </p>
          </div>

          {/* Mode Switcher */}
          <div style={{
            display: 'flex',
            background: 'var(--bg-secondary)',
            padding: '0.25rem',
            borderRadius: '8px',
            border: '1px solid var(--border-color)'
          }}>
            <button
              type="button"
              onClick={() => setMode('LIVE')}
              style={{
                padding: '0.35rem 0.75rem',
                fontSize: '0.8rem',
                fontWeight: 600,
                borderRadius: '6px',
                border: 'none',
                cursor: 'pointer',
                background: mode === 'LIVE' ? 'var(--accent-blue)' : 'transparent',
                color: mode === 'LIVE' ? '#fff' : 'var(--text-muted)'
              }}
            >
              🟢 Live Backend (Port 8080)
            </button>
            <button
              type="button"
              onClick={() => setMode('MOCK')}
              style={{
                padding: '0.35rem 0.75rem',
                fontSize: '0.8rem',
                fontWeight: 600,
                borderRadius: '6px',
                border: 'none',
                cursor: 'pointer',
                background: mode === 'MOCK' ? '#f59e0b' : 'transparent',
                color: mode === 'MOCK' ? '#000' : 'var(--text-muted)'
              }}
            >
              🧪 Offline Demo Mock
            </button>
          </div>
        </div>

        {/* Sample Query Chips */}
        <div style={{ marginBottom: '1rem' }}>
          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginBottom: '0.4rem', fontWeight: 600 }}>
            SAMPLE VERIFICATION SCENARIOS:
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
            {SAMPLE_QUERIES.map((sq, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => selectSample(sq)}
                style={{
                  background: 'var(--bg-secondary)',
                  border: '1px solid var(--border-color)',
                  color: 'var(--text-secondary)',
                  padding: '0.35rem 0.65rem',
                  borderRadius: '6px',
                  fontSize: '0.78rem',
                  cursor: 'pointer',
                  textAlign: 'left'
                }}
              >
                <strong>{sq.label}</strong> → <span style={{ color: 'var(--text-muted)' }}>{sq.expectedOutcome}</span>
              </button>
            ))}
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '1rem' }}>
            <textarea
              className="form-input"
              rows={3}
              placeholder="Ask a question about your policies (e.g. 'What is the required web session timeout?')..."
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              style={{ resize: 'vertical' }}
            />
          </div>

          {error && (
            <div className="alert alert-error" style={{ marginBottom: '1rem' }}>
              ⚠️ {error}
              {mode === 'LIVE' && (
                <div style={{ marginTop: '0.4rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  Tip: If your local backend is not currently running, switch to <strong>Offline Demo Mock</strong> above to preview full verification behavior.
                </div>
              )}
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              Execution mode: <strong>{mode === 'LIVE' ? 'Actual Backend Pipeline' : 'Synthetic Paradigm Demo'}</strong>
            </span>

            <button
              type="submit"
              className="btn btn-primary"
              disabled={loading || !question.trim()}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}
            >
              {loading ? (
                <>
                  <div className="spinner" style={{ width: '14px', height: '14px', borderWidth: '2px' }} />
                  Verifying Across Corpus...
                </>
              ) : (
                '🛡️ Submit Query to TruthGuard'
              )}
            </button>
          </div>
        </form>
      </div>

      {/* Render Answer and Provenance */}
      {activeResponse && <AnswerDisplay response={activeResponse} />}
    </div>
  );
};
