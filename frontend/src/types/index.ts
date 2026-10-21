/**
 * TruthGuard Unified Frontend Types
 */

export type AuthorityLevel = 'DRAFT' | 'WORKING_GROUP' | 'OFFICIAL' | 'EXECUTIVE' | 'REGULATORY';

export type SourceType = 'POLICY' | 'SPECIFICATION' | 'INCIDENT_REPORT' | 'MEETING_MINUTES' | 'SLO' | 'MEMO';

export type ProcessingStatus = 'UPLOADED' | 'PROCESSING' | 'PROCESSED' | 'FAILED';

export type ResolutionStatus = 'ANSWERED' | 'ANSWERED_WITH_QUALIFICATION' | 'CONFLICT' | 'INSUFFICIENT_EVIDENCE';

export type QueryStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'ABSTAINED' | 'FAILED';

export type StageStatus = 'STARTED' | 'SUCCESS' | 'FAILED' | 'SKIPPED';

export interface DocumentVersion {
  id: string;
  documentId: string;
  versionIdentifier: string;
  uploadTimestamp: string;
  effectiveDate?: string | null;
  authorityLevel?: AuthorityLevel | string;
  contentHash: string;
  processingStatus: ProcessingStatus;
  supersedesVersionId?: string | null;
  supersededById?: string | null;
  rawContent?: string | null;
}

export interface DocumentItem {
  id: string;
  title: string;
  sourceType: SourceType | string;
  description?: string | null;
  createdAt: string;
  updatedAt: string;
  versions?: DocumentVersion[];
}

export interface DocumentUploadRequest {
  file: File;
  title: string;
  sourceType: SourceType | string;
  versionIdentifier?: string;
  effectiveDate?: string;
  authorityLevel?: AuthorityLevel | string;
  description?: string;
  supersedesVersionId?: string;
}

export interface EvidenceReference {
  chunk_id: string;
  document_id: string;
  version_id: string;
  source_title?: string;
  version_identifier?: string;
  authority_level?: string;
  effective_date?: string;
  page_number?: number;
  snippet: string;
}

export interface ConflictingPosition {
  passage_id: string;
  source_title?: string;
  version_identifier?: string;
  quotation: string;
  assertion: string;
  authority_level?: string;
  effective_date?: string;
  upload_timestamp?: string;
}

export interface RejectedClaim {
  claim_id: string;
  claim_text: string;
  rejection_reason: string;
  invalid_chunk_ids?: string[];
}

export interface QueryStageTrace {
  id: string;
  stage_name: string;
  status: StageStatus;
  duration_ms: number;
  details?: string;
  created_at: string;
}

export interface QuestionQueryResponse {
  query_id: string;
  correlation_id: string;
  question: string;
  status: QueryStatus;
  resolution_status: ResolutionStatus;
  final_answer?: string | null;
  explanation: string;
  evidence_references: EvidenceReference[];
  conflicting_positions: ConflictingPosition[];
  rejected_claims: RejectedClaim[];
  uncertainty_notes: string[];
  stage_traces: QueryStageTrace[];
  total_duration_ms: number;
  created_at: string;
  completed_at?: string;
  metadata?: Record<string, any>;
  is_mock?: boolean;
}

export interface ServiceHealth {
  status: 'UP' | 'DOWN' | 'CHECKING';
  service?: string;
  version?: string;
  timestamp?: string;
  details?: Record<string, any>;
  error?: string;
}

export interface SystemStatusState {
  backend: ServiceHealth;
  aiService: ServiceHealth;
}
