import React, { useState } from 'react';
import { uploadDocument } from '../api/client';
import { AuthorityLevel, ProcessingStatus, SourceType } from '../types';

interface Props {
  onUploadSuccess?: () => void;
}

export const DocumentUpload: React.FC<Props> = ({ onUploadSuccess }) => {
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [sourceType, setSourceType] = useState<SourceType>('POLICY');
  const [versionIdentifier, setVersionIdentifier] = useState('v1.0');
  const [effectiveDate, setEffectiveDate] = useState('');
  const [authorityLevel, setAuthorityLevel] = useState<AuthorityLevel>('OFFICIAL');
  const [description, setDescription] = useState('');

  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [successInfo, setSuccessInfo] = useState<{ id: string; status: ProcessingStatus; version: string } | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selected = e.target.files[0];
      setFile(selected);
      if (!title) {
        const defaultTitle = selected.name.replace(/\.[^/.]+$/, '').replace(/[-_]/g, ' ');
        setTitle(defaultTitle.charAt(0).toUpperCase() + defaultTitle.slice(1));
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setError('Please select a file to upload (.txt, .pdf, or .docx)');
      return;
    }
    if (!title.trim()) {
      setError('Document title is required');
      return;
    }

    setUploading(true);
    setProgress(10);
    setError(null);
    setSuccessInfo(null);

    try {
      const result = await uploadDocument(
        {
          file,
          title,
          sourceType,
          versionIdentifier,
          effectiveDate: effectiveDate || undefined,
          authorityLevel,
          description: description || undefined
        },
        (p) => setProgress(p)
      );

      setSuccessInfo({
        id: result.id,
        status: result.processingStatus,
        version: result.versionIdentifier
      });

      // Reset form
      setFile(null);
      setTitle('');
      setDescription('');
      setProgress(100);

      if (onUploadSuccess) {
        onUploadSuccess();
      }
    } catch (err: any) {
      setError(err.message || 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="card" style={{ padding: '1.5rem', marginBottom: '2rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.2rem', color: 'var(--text-primary)' }}>
            📁 Document Ingestion Pipeline
          </h3>
          <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            Upload policies, specifications, and reports. Plain text, PDF, and DOCX are verified and versioned.
          </p>
        </div>
        <span className="badge badge-info">Spring Boot Multipart Ingestion</span>
      </div>

      <form onSubmit={handleSubmit}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem', marginBottom: '1rem' }}>
          <div>
            <label className="form-label">Document Title *</label>
            <input
              type="text"
              className="form-input"
              placeholder="e.g. Data Security & Encryption Standard"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
          </div>

          <div>
            <label className="form-label">Source Classification</label>
            <select
              className="form-input"
              value={sourceType}
              onChange={(e) => setSourceType(e.target.value as SourceType)}
            >
              <option value="POLICY">Policy</option>
              <option value="SPECIFICATION">Specification / Architecture</option>
              <option value="INCIDENT_REPORT">Incident Postmortem</option>
              <option value="SLO">SLO / SLA Commitment</option>
              <option value="MEETING_MINUTES">Meeting Minutes</option>
              <option value="MEMO">Internal Memo</option>
            </select>
          </div>

          <div>
            <label className="form-label">Version Identifier</label>
            <input
              type="text"
              className="form-input"
              placeholder="e.g. v1.0, v2.1"
              value={versionIdentifier}
              onChange={(e) => setVersionIdentifier(e.target.value)}
            />
          </div>

          <div>
            <label className="form-label">Effective Policy Date</label>
            <input
              type="date"
              className="form-input"
              value={effectiveDate}
              onChange={(e) => setEffectiveDate(e.target.value)}
            />
          </div>

          <div>
            <label className="form-label">Authority Level</label>
            <select
              className="form-input"
              value={authorityLevel}
              onChange={(e) => setAuthorityLevel(e.target.value as AuthorityLevel)}
            >
              <option value="DRAFT">DRAFT (Rank 1 - Working Proposal)</option>
              <option value="WORKING_GROUP">WORKING GROUP (Rank 2 - Consensus)</option>
              <option value="OFFICIAL">OFFICIAL (Rank 3 - Standard Policy)</option>
              <option value="EXECUTIVE">EXECUTIVE (Rank 4 - Directive)</option>
              <option value="REGULATORY">REGULATORY (Rank 5 - Legal Mandate)</option>
            </select>
          </div>

          <div>
            <label className="form-label">File (.txt, .pdf, .docx) *</label>
            <input
              type="file"
              className="form-input"
              accept=".txt,.pdf,.docx"
              onChange={handleFileChange}
              required
            />
          </div>
        </div>

        <div style={{ marginBottom: '1.25rem' }}>
          <label className="form-label">Description / Scope Notes (Optional)</label>
          <input
            type="text"
            className="form-input"
            placeholder="e.g. Applies to US production databases and microservices"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>

        {uploading && (
          <div style={{ marginBottom: '1rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.25rem' }}>
              <span>Uploading and extracting text...</span>
              <span>{progress}%</span>
            </div>
            <div style={{ height: '6px', background: 'var(--bg-secondary)', borderRadius: '3px', overflow: 'hidden' }}>
              <div style={{ width: `${progress}%`, height: '100%', background: 'var(--accent-blue)', transition: 'width 0.3s ease' }} />
            </div>
          </div>
        )}

        {error && (
          <div className="alert alert-error" style={{ marginBottom: '1rem' }}>
            ⚠️ {error}
          </div>
        )}

        {successInfo && (
          <div className="alert alert-success" style={{ marginBottom: '1rem' }}>
            ✓ Successfully ingested <strong>{successInfo.version}</strong> (ID: {successInfo.id}). Processing Status: <strong>{successInfo.status}</strong>
          </div>
        )}

        <button
          type="submit"
          className="btn btn-primary"
          disabled={uploading || !file}
          style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}
        >
          {uploading ? 'Processing Document...' : 'Upload Document to TruthGuard'}
        </button>
      </form>
    </div>
  );
};
