"""Schemas for RAG chunking, embedding, and indexing."""

from enum import Enum
from typing import Any, Dict, List, Optional
from uuid import UUID, uuid4
from pydantic import BaseModel, Field


class IndexingStatus(str, Enum):
    NOT_INDEXED = "NOT_INDEXED"
    INDEXING = "INDEXING"
    INDEXED = "INDEXED"
    FAILED = "FAILED"


class DocumentChunk(BaseModel):
    """Represents an atomic, searchable text chunk with embedding vector."""
    id: UUID = Field(default_factory=uuid4, description="Unique chunk identifier")
    document_id: UUID = Field(..., description="Parent document UUID")
    version_id: UUID = Field(..., description="Specific DocumentVersion UUID")
    chunk_index: int = Field(..., description="Zero-based sequence index within the version")
    chunk_text: str = Field(..., description="Clean textual excerpt of the chunk")
    embedding: Optional[List[float]] = Field(None, description="Dense vector embedding")
    page_number: Optional[int] = Field(None, description="Source page number if available (e.g. from PDF)")
    source_title: Optional[str] = Field(None, description="Original document title")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="Custom and provenance metadata")


class PageContent(BaseModel):
    """Optional page segment passed for multi-page documents."""
    page_number: int = Field(..., description="Page number (1-indexed)")
    text: str = Field(..., description="Text content on this page")


class IndexVersionRequest(BaseModel):
    """Request payload to chunk, embed, and index a document version."""
    document_id: UUID = Field(..., description="UUID of the parent document")
    version_id: UUID = Field(..., description="UUID of the document version being indexed")
    text: str = Field(..., description="Extracted raw text content of the document version")
    source_title: Optional[str] = Field(None, description="Human-readable title of the document")
    version_identifier: Optional[str] = Field(None, description="Human-readable version string, e.g. 'v1.0'")
    effective_date: Optional[str] = Field(None, description="ISO format effective date")
    upload_timestamp: Optional[str] = Field(None, description="ISO format upload timestamp")
    authority_level: Optional[str] = Field(None, description="Authority classification e.g. OFFICIAL, WORKING_DRAFT")
    source_type: Optional[str] = Field(None, description="Document type e.g. POLICY, SPECIFICATION")
    supersedes_version_id: Optional[UUID] = Field(None, description="UUID of older superseded version")
    superseded_by_id: Optional[UUID] = Field(None, description="UUID of newer superseding version")
    metadata: Optional[Dict[str, Any]] = Field(default_factory=dict, description="Metadata to attach to chunks")
    chunk_size: Optional[int] = Field(None, description="Maximum characters per chunk (defaults to server config)")
    chunk_overlap: Optional[int] = Field(None, description="Overlapping characters between chunks")
    pages: Optional[List[PageContent]] = Field(None, description="Optional page-segmented content")


class IndexVersionResponse(BaseModel):
    """Response returned upon completing version indexing."""
    document_id: UUID
    version_id: UUID
    status: IndexingStatus
    chunk_count: int
    embedding_model: str
    embedding_dimension: int
    error_message: Optional[str] = None


class ChunkInspectResponse(BaseModel):
    """Inspectable representation of a stored chunk with embedding summary."""
    id: UUID
    document_id: UUID
    version_id: UUID
    chunk_index: int
    chunk_text: str
    page_number: Optional[int]
    source_title: Optional[str]
    embedding_dimension: int
    embedding_sample: List[float] = Field(..., description="Preview of first 5 embedding dimensions")
    embedding: Optional[List[float]] = Field(None, description="Full embedding vector if requested")
    metadata: Dict[str, Any]


class VersionChunksResponse(BaseModel):
    """List of all chunks stored for a document version."""
    version_id: UUID
    document_id: UUID
    total_chunks: int
    chunks: List[ChunkInspectResponse]


class IndexingStatusResponse(BaseModel):
    """Current indexing status for a document version."""
    version_id: UUID
    document_id: UUID
    status: IndexingStatus
    chunk_count: int
    embedding_model: str
    embedding_dimension: int
    error_message: Optional[str] = None
    updated_at: Optional[str] = None
