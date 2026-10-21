"""Pydantic schemas for TruthGuard Retriever Agent."""

from datetime import date
from typing import Any, Dict, List, Optional
from uuid import UUID
from pydantic import BaseModel, Field


class RetrievalFilter(BaseModel):
    """Optional metadata and provenance filters for evidence retrieval."""
    document_ids: Optional[List[UUID]] = Field(None, description="Filter to specific document UUIDs")
    version_ids: Optional[List[UUID]] = Field(None, description="Filter to specific document version UUIDs")
    source_types: Optional[List[str]] = Field(None, description="Filter by source type (e.g. POLICY, SPECIFICATION)")
    authority_levels: Optional[List[str]] = Field(None, description="Filter by authority (e.g. OFFICIAL, WORKING_DRAFT)")
    effective_date_from: Optional[date] = Field(None, description="Filter versions effective on or after date")
    effective_date_to: Optional[date] = Field(None, description="Filter versions effective on or before date")
    include_superseded: bool = Field(
        default=True,
        description="Preserve older superseded versions so the critic can inspect conflicts and verify supersession"
    )


class RetrievalRequest(BaseModel):
    """Request payload to query the Retriever Agent."""
    question: str = Field(..., min_length=1, description="Question or claim query to retrieve evidence for")
    filters: Optional[RetrievalFilter] = Field(default=None, description="Optional metadata and version filters")
    top_k: int = Field(default=5, ge=1, le=100, description="Maximum number of ranked passages to return")
    min_score: float = Field(default=0.0, ge=0.0, le=1.0, description="Minimum combined relevance threshold")
    semantic_weight: float = Field(
        default=0.7,
        ge=0.0,
        le=1.0,
        description="Weight alpha for dense semantic similarity [0.0 = keyword only, 1.0 = semantic only]"
    )
    ranking_method: str = Field(
        default="hybrid_linear",
        description="Ranking combination strategy: 'hybrid_linear' (normalized weighted sum) or 'rrf' (Reciprocal Rank Fusion)"
    )


class RetrievedPassage(BaseModel):
    """Ranked evidence passage with comprehensive provenance and scores."""
    chunk_id: UUID = Field(..., description="Unique chunk UUID")
    document_id: UUID = Field(..., description="Parent document UUID")
    version_id: UUID = Field(..., description="DocumentVersion UUID")
    version_identifier: Optional[str] = Field(None, description="Human-readable version tag (e.g. 'v1.0', 'v2.0')")
    chunk_index: int = Field(..., description="Zero-based sequence index within the version")
    text: str = Field(..., description="Passage text excerpt")
    score: float = Field(..., description="Final combined relevance score")
    semantic_score: Optional[float] = Field(None, description="Dense vector cosine similarity")
    keyword_score: Optional[float] = Field(None, description="Lexical keyword match score")
    source_title: Optional[str] = Field(None, description="Title of the source document")
    source_type: Optional[str] = Field(None, description="Source classification type (e.g. POLICY, REGULATION)")
    page_number: Optional[int] = Field(None, description="Page number where the passage appears")
    effective_date: Optional[str] = Field(None, description="Date version took effect")
    upload_timestamp: Optional[str] = Field(None, description="Timestamp version was ingested")
    authority_level: Optional[str] = Field(None, description="Authority level (e.g. OFFICIAL, WORKING_DRAFT)")
    supersedes_version_id: Optional[UUID] = Field(None, description="UUID of older version superseded by this version")
    superseded_by_id: Optional[UUID] = Field(None, description="UUID of newer version superseding this version")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="Custom provenance metadata")


class RetrievalResponse(BaseModel):
    """Response returned by the Retriever Agent."""
    question: str = Field(..., description="The query evaluated")
    total_retrieved: int = Field(..., description="Number of passages meeting criteria")
    passages: List[RetrievedPassage] = Field(..., description="Ranked list of retrieved evidence passages")
    retrieval_metadata: Dict[str, Any] = Field(default_factory=dict, description="Retrieval settings and execution trace")
