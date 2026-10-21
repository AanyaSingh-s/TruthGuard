"""Pydantic schemas for TruthGuard Answer Synthesizer Agent."""

from enum import Enum
from typing import Any, Dict, List, Optional
from uuid import UUID
from pydantic import BaseModel, Field

from app.schemas.retrieval import RetrievalFilter, RetrievedPassage


class ClaimType(str, Enum):
    """Categorization of individual claims in the candidate answer."""
    FACT = "FACT"
    CONFLICT = "CONFLICT"
    UNCERTAIN = "UNCERTAIN"
    ASSUMPTION = "ASSUMPTION"


class AtomicClaim(BaseModel):
    """An individual verifiable claim extracted from the candidate answer."""
    claim_id: str = Field(..., description="Unique claim identifier within the answer (e.g. 'claim-1')")
    text: str = Field(..., description="Atomic claim assertion")
    claim_type: ClaimType = Field(default=ClaimType.FACT, description="Claim classification")
    cited_chunk_ids: List[UUID] = Field(
        default_factory=list,
        description="Raw chunk UUIDs cited by the language model"
    )
    valid_chunk_ids: List[UUID] = Field(
        default_factory=list,
        description="Chunk UUIDs verified against the supplied retrieval context"
    )
    invalid_chunk_ids: List[str] = Field(
        default_factory=list,
        description="Citations that do not exist in the supplied evidence (hallucinations)"
    )
    is_grounded: bool = Field(
        default=False,
        description="True only if the claim has at least one valid citation and zero invalid citations"
    )
    unsupported_reason: Optional[str] = Field(
        default=None,
        description="Explanation if claim is ungrounded or contains bogus citations"
    )


class SynthesisRequest(BaseModel):
    """Typed input payload containing question and retrieved evidence with provenance."""
    question: str = Field(..., min_length=1, description="The question to synthesize an answer for")
    passages: Optional[List[RetrievedPassage]] = Field(
        default=None,
        description="Retrieved evidence passages with complete version provenance. If omitted, retriever is invoked."
    )
    filters: Optional[RetrievalFilter] = Field(
        default=None,
        description="Optional filters passed to retriever if passages are omitted"
    )
    top_k: Optional[int] = Field(
        default=5,
        ge=1,
        le=100,
        description="Number of passages to retrieve if passages are omitted"
    )
    temperature: Optional[float] = Field(None, ge=0.0, le=1.0, description="Model generation temperature")
    max_tokens: Optional[int] = Field(None, ge=64, le=4096, description="Maximum token length for generation")


class RawLLMSynthesisOutput(BaseModel):
    """Internal model for initial parsing of raw JSON output from the LLM."""
    candidate_answer: str = Field(..., description="Narrative candidate answer")
    claims: List[Dict[str, Any]] = Field(default_factory=list, description="Raw atomic claims list")
    uncertainty_notes: List[str] = Field(default_factory=list, description="Noted ambiguities or uncertainties")
    missing_information: List[str] = Field(default_factory=list, description="Missing evidence or unanswered aspects")


class SynthesisResponse(BaseModel):
    """Validated structured candidate answer with verified evidence provenance."""
    question: str = Field(..., description="Question evaluated")
    candidate_answer: str = Field(..., description="Synthesized candidate answer")
    claims: List[AtomicClaim] = Field(..., description="Validated atomic claims with citation checks")
    total_claims: int = Field(..., description="Total claims extracted")
    supported_claims_count: int = Field(..., description="Number of claims with valid evidence citations")
    unsupported_claims_count: int = Field(..., description="Number of claims lacking valid citations")
    is_fully_grounded: bool = Field(..., description="True if 100% of claims are grounded with valid citations")
    uncertainty_notes: List[str] = Field(default_factory=list, description="Uncertainties identified in evidence")
    missing_information: List[str] = Field(default_factory=list, description="Information missing from evidence")
    evidence_chunk_ids: List[UUID] = Field(
        default_factory=list,
        description="All valid chunk IDs supplied in the input context"
    )
    metadata: Dict[str, Any] = Field(
        default_factory=dict,
        description="Execution metadata including provider, model, and citation validation stats"
    )
