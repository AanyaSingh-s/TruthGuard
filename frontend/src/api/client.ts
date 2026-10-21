/**
 * TruthGuard Typed API Client
 * Separates network communication from UI presentation.
 */

import {
  DocumentItem,
  DocumentUploadRequest,
  DocumentVersion,
  QuestionQueryResponse,
  ServiceHealth
} from '../types';

const BACKEND_BASE = (import.meta as any).env?.VITE_BACKEND_URL || 'http://localhost:8080';
const AI_SERVICE_BASE = (import.meta as any).env?.VITE_AI_SERVICE_URL || 'http://localhost:8000';

export class ApiError extends Error {
  public status: number;
  public details?: any;

  constructor(message: string, status: number, details?: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

/**
 * Health check probes for backend and AI service.
 */
export async function checkBackendHealth(): Promise<ServiceHealth> {
  try {
    const res = await fetch(`${BACKEND_BASE}/api/v1/health`, { method: 'GET' });
    if (!res.ok) {
      return { status: 'DOWN', error: `HTTP ${res.status}: ${res.statusText}` };
    }
    const data = await res.json();
    return {
      status: 'UP',
      service: data.service || 'truthguard-backend',
      version: data.version || '0.1.0',
      timestamp: data.timestamp || new Date().toISOString(),
      details: data
    };
  } catch (err: any) {
    return { status: 'DOWN', error: err.message || 'Cannot reach Spring Boot backend' };
  }
}

export async function checkAiServiceHealth(): Promise<ServiceHealth> {
  try {
    const res = await fetch(`${AI_SERVICE_BASE}/health`, { method: 'GET' });
    if (!res.ok) {
      return { status: 'DOWN', error: `HTTP ${res.status}: ${res.statusText}` };
    }
    const data = await res.json();
    return {
      status: 'UP',
      service: data.service || 'truthguard-ai-service',
      version: data.version || '0.1.0',
      timestamp: data.timestamp || new Date().toISOString(),
      details: data
    };
  } catch (err: any) {
    return { status: 'DOWN', error: err.message || 'Cannot reach Python AI service' };
  }
}

/**
 * Upload a document with multipart form data.
 */
export async function uploadDocument(
  req: DocumentUploadRequest,
  onProgress?: (progress: number) => void
): Promise<DocumentVersion> {
  const formData = new FormData();
  formData.append('file', req.file);
  formData.append('title', req.title.trim());
  formData.append('sourceType', req.sourceType);

  if (req.versionIdentifier) {
    formData.append('versionIdentifier', req.versionIdentifier.trim());
  }
  if (req.effectiveDate) {
    formData.append('effectiveDate', req.effectiveDate);
  }
  if (req.authorityLevel) {
    formData.append('authorityLevel', req.authorityLevel);
  }
  if (req.description) {
    formData.append('description', req.description);
  }
  if (req.supersedesVersionId) {
    formData.append('supersedesVersionId', req.supersedesVersionId);
  }

  if (onProgress) onProgress(30);

  const res = await fetch(`${BACKEND_BASE}/api/v1/documents/upload`, {
    method: 'POST',
    body: formData
  });

  if (onProgress) onProgress(80);

  if (!res.ok) {
    let errorMsg = `Upload failed with status ${res.status}`;
    try {
      const errJson = await res.json();
      errorMsg = errJson.message || errJson.error || errorMsg;
    } catch (_) {}
    throw new ApiError(errorMsg, res.status);
  }

  if (onProgress) onProgress(100);
  return await res.json();
}

/**
 * Retrieve all registered documents and their versions.
 */
export async function getDocuments(): Promise<DocumentItem[]> {
  const res = await fetch(`${BACKEND_BASE}/api/v1/documents`, { method: 'GET' });
  if (!res.ok) {
    let errorMsg = `Failed to fetch documents (${res.status})`;
    try {
      const errJson = await res.json();
      errorMsg = errJson.message || errorMsg;
    } catch (_) {}
    throw new ApiError(errorMsg, res.status);
  }
  return await res.json();
}

/**
 * Submit an evidence-grounded QA query through the complete pipeline.
 */
export async function askQuestion(
  question: string,
  options?: { topK?: number; minScore?: number; correlationId?: string }
): Promise<QuestionQueryResponse> {
  const payload = {
    question: question.trim(),
    top_k: options?.topK ?? 5,
    min_score: options?.minScore ?? 0.0,
    correlation_id: options?.correlationId || `web-${Date.now().toString(36)}`
  };

  const res = await fetch(`${BACKEND_BASE}/api/v1/queries`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    let errorMsg = `Query failed with status ${res.status}`;
    try {
      const errJson = await res.json();
      errorMsg = errJson.message || errJson.error || errorMsg;
    } catch (_) {}
    throw new ApiError(errorMsg, res.status);
  }

  return await res.json();
}

/**
 * Realistic synthetic sample scenarios for Demonstration / Offline Mock Mode.
 * These are strictly flagged with is_mock = true.
 */
export function getMockResponse(
  scenario: 'ANSWERED' | 'QUALIFIED' | 'CONFLICT' | 'INSUFFICIENT'
): QuestionQueryResponse {
  const now = new Date().toISOString();

  if (scenario === 'ANSWERED') {
    return {
      query_id: 'mock-query-001',
      correlation_id: 'mock-corr-001',
      question: 'What TLS encryption standard is required for production database connections?',
      status: 'COMPLETED',
      resolution_status: 'ANSWERED',
      final_answer: 'According to the Infrastructure Security Standard (v2.0, effective 2025-01-01), all production database client connections must enforce TLS 1.3 encryption with certificate validation.',
      explanation: 'Candidate answer is fully substantiated by consistent and authoritative evidence.',
      evidence_references: [
        {
          chunk_id: 'c8f712a4-7932-4d2b-b8f9-9a0d84bf2001',
          document_id: 'd1010101-0000-0000-0000-000000000001',
          version_id: 'v2020202-0000-0000-0000-000000000002',
          source_title: 'Infrastructure Security Standard',
          version_identifier: 'v2.0',
          authority_level: 'OFFICIAL',
          effective_date: '2025-01-01',
          page_number: 4,
          snippet: 'Production database instances must reject unencrypted connections and require TLS 1.3 or higher with mutual certificate verification for all internal microservices.'
        }
      ],
      conflicting_positions: [],
      rejected_claims: [],
      uncertainty_notes: [],
      stage_traces: [
        { id: 'st-1', stage_name: 'REQUEST_VALIDATION', status: 'SUCCESS', duration_ms: 2, details: 'Question verified', created_at: now },
        { id: 'st-2', stage_name: 'AI_ANALYSIS', status: 'SUCCESS', duration_ms: 245, details: 'Retrieved 3 passages, 1 claim extracted, Critic verdict: PASS', created_at: now },
        { id: 'st-3', stage_name: 'RESOLUTION_ARBITRATION', status: 'SUCCESS', duration_ms: 5, details: 'Consistent evidence, claim validated', created_at: now },
        { id: 'st-4', stage_name: 'RESPONSE_ASSEMBLY', status: 'SUCCESS', duration_ms: 1, details: 'Assembled response with 1 evidence reference', created_at: now }
      ],
      total_duration_ms: 253,
      created_at: now,
      completed_at: now,
      is_mock: true
    };
  }

  if (scenario === 'QUALIFIED') {
    return {
      query_id: 'mock-query-002',
      correlation_id: 'mock-corr-002',
      question: 'What is the required web session idle timeout under corporate access policy?',
      status: 'COMPLETED',
      resolution_status: 'ANSWERED_WITH_QUALIFICATION',
      final_answer: 'Web session idle timeout is 60 minutes.\n\n[TruthGuard Qualifications:\n• Resolved via explicit supersession: Access Control Policy v2.0 (effective 2025-01-01) explicitly supersedes Access Control Policy v1.0 (effective 2024-01-01) which specified 30 minutes.]',
      explanation: 'Answer validated with deterministic qualifications: Resolved via explicit supersession: Access Control Policy v2.0 explicitly supersedes Access Control Policy v1.0',
      evidence_references: [
        {
          chunk_id: 'chunk-v2-active',
          document_id: 'doc-access-policy',
          version_id: 'ver-2-active',
          source_title: 'Access Control Policy v2.0',
          version_identifier: 'v2.0',
          authority_level: 'OFFICIAL',
          effective_date: '2025-01-01',
          page_number: 2,
          snippet: 'Section 4.1: Web session idle timeout is extended to 60 minutes for authenticated staff portal sessions.'
        }
      ],
      conflicting_positions: [],
      rejected_claims: [],
      uncertainty_notes: ['Older policy (v1.0) mandated 30 minutes, but v2.0 explicitly supersedes it with a newer effective date.'],
      stage_traces: [
        { id: 'st-1', stage_name: 'REQUEST_VALIDATION', status: 'SUCCESS', duration_ms: 2, details: 'Question verified', created_at: now },
        { id: 'st-2', stage_name: 'AI_ANALYSIS', status: 'SUCCESS', duration_ms: 310, details: 'Retrieved 2 passages, Critic verdict: CONFLICT (staleness noted)', created_at: now },
        { id: 'st-3', stage_name: 'RESOLUTION_ARBITRATION', status: 'SUCCESS', duration_ms: 6, details: 'Explicit supersession resolved: v2.0 supersedes v1.0', created_at: now },
        { id: 'st-4', stage_name: 'RESPONSE_ASSEMBLY', status: 'SUCCESS', duration_ms: 1, details: 'Assembled qualified answer', created_at: now }
      ],
      total_duration_ms: 319,
      created_at: now,
      completed_at: now,
      is_mock: true
    };
  }

  if (scenario === 'CONFLICT') {
    return {
      query_id: 'mock-query-003',
      correlation_id: 'mock-corr-003',
      question: 'What is the mandatory web session timeout across company systems?',
      status: 'ABSTAINED',
      resolution_status: 'CONFLICT',
      final_answer: null,
      explanation: 'Unresolvable contradiction between equally authoritative, equally current sources regarding: Web Session Timeout. TruthGuard preserves the conflict and abstains from averaging contradictory claims.',
      evidence_references: [],
      conflicting_positions: [
        {
          passage_id: 'chunk-secops-01',
          source_title: 'SecOps Operational Standard',
          version_identifier: 'SecOps-2025',
          quotation: 'All corporate web sessions must terminate after strictly 30 minutes of inactivity.',
          assertion: '30-minute session timeout mandatory',
          authority_level: 'OFFICIAL',
          effective_date: '2025-01-01',
          upload_timestamp: '2025-01-05T09:00:00Z'
        },
        {
          passage_id: 'chunk-it-01',
          source_title: 'Corporate IT Guidelines',
          version_identifier: 'CorpIT-2025',
          quotation: 'Employee browser sessions shall remain active for up to 60 minutes before session expiration.',
          assertion: '60-minute session timeout permitted',
          authority_level: 'OFFICIAL',
          effective_date: '2025-01-01',
          upload_timestamp: '2025-01-05T11:00:00Z'
        }
      ],
      rejected_claims: [],
      uncertainty_notes: ['Both standards share identical OFFICIAL authority rank and identical 2025-01-01 effective dates without an explicit supersession link.'],
      stage_traces: [
        { id: 'st-1', stage_name: 'REQUEST_VALIDATION', status: 'SUCCESS', duration_ms: 1, details: 'Valid query', created_at: now },
        { id: 'st-2', stage_name: 'AI_ANALYSIS', status: 'SUCCESS', duration_ms: 280, details: 'Critic detected direct contradiction on Web Session Timeout', created_at: now },
        { id: 'st-3', stage_name: 'RESOLUTION_ARBITRATION', status: 'SUCCESS', duration_ms: 7, details: 'Preserved conflict: equal authority (OFFICIAL) & identical effective date', created_at: now },
        { id: 'st-4', stage_name: 'RESPONSE_ASSEMBLY', status: 'SUCCESS', duration_ms: 1, details: 'Abstained from blending contradictory policies', created_at: now }
      ],
      total_duration_ms: 289,
      created_at: now,
      completed_at: now,
      is_mock: true
    };
  }

  // INSUFFICIENT
  return {
    query_id: 'mock-query-004',
    correlation_id: 'mock-corr-004',
    question: 'What is the maximum allowed reimbursement budget for offsite team dinners in Tokyo?',
    status: 'ABSTAINED',
    resolution_status: 'INSUFFICIENT_EVIDENCE',
    final_answer: null,
    explanation: 'Insufficient evidence: retrieved evidence set is empty or does not contain facts necessary to answer the question.',
    evidence_references: [],
    conflicting_positions: [],
    rejected_claims: [
      {
        claim_id: 'c1',
        claim_text: 'Tokyo offsite dinner budget is 15,000 JPY per person.',
        rejection_reason: 'Hallucinated or unverified evidence chunk ID: [chunk-tokyo-fake]'
      }
    ],
    uncertainty_notes: ['Retrieved documents contain general travel policies but omit Tokyo-specific team dinner allowances.'],
    stage_traces: [
      { id: 'st-1', stage_name: 'REQUEST_VALIDATION', status: 'SUCCESS', duration_ms: 1, details: 'Valid query', created_at: now },
      { id: 'st-2', stage_name: 'AI_ANALYSIS', status: 'SUCCESS', duration_ms: 180, details: 'Critic verdict: INSUFFICIENT_EVIDENCE, ungrounded claim detected', created_at: now },
      { id: 'st-3', stage_name: 'RESOLUTION_ARBITRATION', status: 'SUCCESS', duration_ms: 3, details: 'Abstained: all claims ungrounded in corpus', created_at: now },
      { id: 'st-4', stage_name: 'RESPONSE_ASSEMBLY', status: 'SUCCESS', duration_ms: 1, details: 'Abstention response assembled', created_at: now }
    ],
    total_duration_ms: 185,
    created_at: now,
    completed_at: now,
    is_mock: true
  };
}
