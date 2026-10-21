"""Pydantic schemas for TruthGuard Critic/Adversary Agent."""

from enum import Enum
from typing import Any, Dict, List, Optional
from uuid import UUID
from pydantic import BaseModel, Field

from app.schemas.retrieval import RetrievedPassage
from app.schemas.synthesis import AtomicClaim


class CriticVerdict(str, Enum):
    """The critical assessment verdict for a candidate answer."""
    PASS = "PASS"
    CONFLICT = "CONFLICT"
    UNSUPPORTED = "UNSUPPORTED"
    INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE"


class SourcePosition(BaseModel):
    """A distinct assertion or viewpoint attributed to a specific source version."""
    passage_id: UUID = Field(..., description="UUID of the supporting evidence chunk")
    source_title: Optional[str] = Field(None, description="Title of the source document")
    version_identifier: Optional[str] = Field(None, description="Document version tag (e.g. 'v1.0')")
    quotation: str = Field(..., description="Verbatim excerpt or quotation illustrating the position")
    assertion: str = Field(..., description="Summary of the assertion made by this source")


class StalenessFinding(BaseModel):
    """Deterministic findings regarding document recency, ordering, and supersession links."""
    older_version_id: Optional[UUID] = Field(None, description="UUID of older version")
    newer_version_id: Optional[UUID] = Field(None, description="UUID of newer version")
    older_version: Optional[str] = Field(None, description="Older version tag (e.g. 'v1.0')")
    newer_version: Optional[str] = Field(None, description="Newer version tag (e.g. 'v2.0')")
    older_effective_date: Optional[str] = Field(None, description="Effective date of older version")
    newer_effective_date: Optional[str] = Field(None, description="Effective date of newer version")
    has_explicit_supersession: bool = Field(
        default=False,
        description="True if explicit supersedes_version_id or superseded_by_id relationship exists in metadata"
    )
    details: str = Field(..., description="Explanation of temporal relationship")


class ContradictionFinding(BaseModel):
    """Details of a detected contradiction between evidence passages or claims."""
    conflicting_claim_ids: List[str] = Field(
        default_factory=list,
        description="IDs of claims involved in the contradiction"
    )
    conflicting_evidence_ids: List[UUID] = Field(
        default_factory=list,
        description="Chunk UUIDs of contradictory passages"
    )
    topic: str = Field(..., description="Specific policy, parameter, or topic being disputed")
    positions: List[SourcePosition] = Field(
        default_factory=list,
        description="Competing positions and quotations"
    )
    scope_aligned: bool = Field(
        default=True,
        description="True if the conflicting statements apply to the same entity, scope, and time period"
    )
    explanation: str = Field(..., description="Analysis of why this constitutes a direct contradiction")


class CriticRequest(BaseModel):
    """Input payload for the Critic Agent."""
    question: str = Field(..., min_length=1, description="Original user question")
    candidate_answer: str = Field(..., description="Candidate answer produced by the Synthesizer")
    claims: List[AtomicClaim] = Field(
        default_factory=list,
        description="Deconstructed atomic claims with citation metadata"
    )
    passages: List[RetrievedPassage] = Field(
        default_factory=list,
        description="Retrieved evidence passages with complete provenance"
    )


class CriticResponse(BaseModel):
    """Comprehensive adversarial review of the candidate answer and underlying evidence."""
    verdict: CriticVerdict = Field(
        ...,
        description="Verdict: PASS, CONFLICT, UNSUPPORTED, or INSUFFICIENT_EVIDENCE"
    )
    confidence: float = Field(
        ...,
        ge=0.0,
        le=1.0,
        description="Confidence score in the critique verdict"
    )
    conflicting_claim_ids: List[str] = Field(
        default_factory=list,
        description="Claim IDs directly involved in contradictions"
    )
    conflicting_evidence_ids: List[UUID] = Field(
        default_factory=list,
        description="Chunk IDs of contradictory evidence"
    )
    unsupported_claim_ids: List[str] = Field(
        default_factory=list,
        description="Claim IDs lacking valid evidentiary support"
    )
    source_positions: List[SourcePosition] = Field(
        default_factory=list,
        description="Extracted competing positions and verbatim quotations"
    )
    staleness_findings: List[StalenessFinding] = Field(
        default_factory=list,
        description="Deterministic temporal and supersession findings"
    )
    contradictions: List[ContradictionFinding] = Field(
        default_factory=list,
        description="Specific contradiction details"
    )
    explanation: str = Field(
        ...,
        description="Summary explanation justifying the final verdict"
    )
    uncertainty_notes: List[str] = Field(
        default_factory=list,
        description="Noted caveats, ambiguities, or open questions"
    )
    metadata: Dict[str, Any] = Field(
        default_factory=dict,
        description="Execution trace, provider info, and verification statistics"
    )
