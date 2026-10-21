"""Orchestrator for RAG chunking, embedding, and idempotent vector persistence."""

import logging
import re
from typing import Any, Dict, List, Optional
from uuid import UUID

from app.config import settings
from app.db.vector_store import VectorStore
from app.schemas.indexing import (
    DocumentChunk,
    IndexingStatus,
    IndexVersionRequest,
    IndexVersionResponse,
    PageContent,
)
from app.services.chunker import TextChunker
from app.services.embedder import EmbeddingError, EmbeddingService

logger = logging.getLogger(__name__)


class IndexingError(Exception):
    """Raised when document indexing fails."""
    pass


class EmptyInputError(IndexingError):
    """Raised when document content provided for indexing is empty."""
    pass


def parse_text_supersession_mentions(text: str) -> Dict[str, Any]:
    """
    Parses 'supersedes/amends/replaces vX / dated <date>' mentions from document text.
    """
    meta: Dict[str, Any] = {}
    if not text:
        return meta

    m_ver = re.search(
        r'(?:supersedes?|amends?|replaces?)\s*[:\-]?\s*(?:version\s*|v\.?\s*)?([0-9]+(?:\.[0-9]+)*[a-zA-Z0-9\-]*)',
        text,
        re.IGNORECASE
    )
    if m_ver:
        meta["supersedes_text_version"] = m_ver.group(1).strip()
        meta["has_text_supersession"] = True

    m_date = re.search(
        r'(?:supersedes?|amends?|replaces?)[^\.\n]*?(?:dated|effective|from)\s*([0-9]{4}[-/][0-9]{1,2}[-/][0-9]{1,2})',
        text,
        re.IGNORECASE
    )
    if m_date:
        meta["supersedes_text_date"] = m_date.group(1).replace("/", "-")
        meta["has_text_supersession"] = True

    return meta


class IndexingService:
    """Coordinates chunking, dense embedding, and transactional idempotent storage."""

    def __init__(
        self,
        chunker: Optional[TextChunker] = None,
        embedder: Optional[EmbeddingService] = None,
        store: Optional[VectorStore] = None
    ):
        self.chunker = chunker or TextChunker()
        self.embedder = embedder or EmbeddingService()
        self.store = store or VectorStore()

    def index_version(self, request: IndexVersionRequest) -> IndexVersionResponse:
        """
        Execute the end-to-end RAG indexing pipeline:
        1. Validates input content.
        2. Splits text into overlapping chunks with preserved provenance.
        3. Computes dense embeddings.
        4. Persists chunks idempotently in PostgreSQL + pgvector.
        """
        if not request.text or not request.text.strip():
            logger.warning("Empty text submitted for indexing version %s", request.version_id)
            raise EmptyInputError(f"Document version '{request.version_id}' contains no text to index")

        logger.info(
            "Starting indexing for version %s (document %s, %d characters)",
            request.version_id, request.document_id, len(request.text)
        )

        # 1. Chunking
        try:
            chunk_metadata = dict(request.metadata or {})
            if request.version_identifier and "version_identifier" not in chunk_metadata:
                chunk_metadata["version_identifier"] = request.version_identifier
            if request.effective_date and "effective_date" not in chunk_metadata:
                chunk_metadata["effective_date"] = request.effective_date
            if request.upload_timestamp and "upload_timestamp" not in chunk_metadata:
                chunk_metadata["upload_timestamp"] = request.upload_timestamp
            if request.authority_level and "authority_level" not in chunk_metadata:
                chunk_metadata["authority_level"] = request.authority_level
            if request.source_type and "source_type" not in chunk_metadata:
                chunk_metadata["source_type"] = request.source_type
            if request.supersedes_version_id and "supersedes_version_id" not in chunk_metadata:
                chunk_metadata["supersedes_version_id"] = str(request.supersedes_version_id)
            if request.superseded_by_id and "superseded_by_id" not in chunk_metadata:
                chunk_metadata["superseded_by_id"] = str(request.superseded_by_id)

            # Parse supersedes/amends/replaces mentions from text into metadata
            parsed_mentions = parse_text_supersession_mentions(request.text)
            chunk_metadata.update(parsed_mentions)

            chunks = self.chunker.chunk_version(
                document_id=request.document_id,
                version_id=request.version_id,
                text=request.text,
                source_title=request.source_title,
                metadata=chunk_metadata,
                chunk_size=request.chunk_size,
                chunk_overlap=request.chunk_overlap,
                pages=request.pages
            )
        except Exception as e:
            logger.error("Chunking failed for version %s: %s", request.version_id, e)
            self.store.record_indexing_failure(
                request.document_id,
                request.version_id,
                f"Chunking error: {str(e)}",
                self.embedder.model_name,
                self.embedder.dimension
            )
            raise IndexingError(f"Failed to chunk version text: {str(e)}") from e

        if not chunks:
            raise EmptyInputError(f"No valid chunks generated for version '{request.version_id}'")

        # 2. Embedding
        try:
            chunk_texts = [c.chunk_text for c in chunks]
            embeddings = self.embedder.embed_batch(chunk_texts)
            for chunk, emb in zip(chunks, embeddings):
                chunk.embedding = emb
        except EmbeddingError as e:
            logger.error("Embedding generation failed for version %s: %s", request.version_id, e)
            self.store.record_indexing_failure(
                request.document_id,
                request.version_id,
                f"Embedding error: {str(e)}",
                self.embedder.model_name,
                self.embedder.dimension
            )
            raise IndexingError(f"Embedding failed: {str(e)}") from e
        except Exception as e:
            logger.error("Unexpected embedding failure for version %s: %s", request.version_id, e, exc_info=True)
            self.store.record_indexing_failure(
                request.document_id,
                request.version_id,
                f"Unexpected embedding failure: {str(e)}",
                self.embedder.model_name,
                self.embedder.dimension
            )
            raise IndexingError(f"Unexpected embedding failure: {str(e)}") from e

        # 3. Idempotent Storage
        try:
            stored_count = self.store.store_chunks_idempotent(
                document_id=request.document_id,
                version_id=request.version_id,
                chunks=chunks,
                embedding_model=self.embedder.model_name,
                embedding_dimension=self.embedder.dimension
            )
        except Exception as e:
            logger.error("Database storage failed for version %s: %s", request.version_id, e, exc_info=True)
            self.store.record_indexing_failure(
                request.document_id,
                request.version_id,
                f"Storage error: {str(e)}",
                self.embedder.model_name,
                self.embedder.dimension
            )
            raise IndexingError(f"Failed to persist chunks in vector store: {str(e)}") from e

        logger.info(
            "Successfully indexed version %s: %d chunks with model %s (%d-d)",
            request.version_id, stored_count, self.embedder.model_name, self.embedder.dimension
        )

        return IndexVersionResponse(
            document_id=request.document_id,
            version_id=request.version_id,
            status=IndexingStatus.INDEXED,
            chunk_count=stored_count,
            embedding_model=self.embedder.model_name,
            embedding_dimension=self.embedder.dimension,
            error_message=None
        )

    def get_version_chunks(self, version_id: UUID) -> List[DocumentChunk]:
        """Inspect all chunks and embeddings for a version."""
        return self.store.get_chunks_by_version(version_id)

    def get_status(self, version_id: UUID) -> Optional[Dict[str, Any]]:
        """Inspect indexing status for a version."""
        return self.store.get_indexing_status(version_id)
