"""RAG vector indexing API endpoints."""

import logging
from uuid import UUID
from fastapi import APIRouter, HTTPException, status

from app.schemas.indexing import (
    ChunkInspectResponse,
    IndexingStatus,
    IndexingStatusResponse,
    IndexVersionRequest,
    IndexVersionResponse,
    VersionChunksResponse,
)
from app.services.indexer import EmptyInputError, IndexingError, IndexingService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1", tags=["RAG Indexing & Embeddings"])

# Service singleton
indexer_service = IndexingService()


@router.post(
    "/index-version",
    response_model=IndexVersionResponse,
    summary="Index document version chunks and vector embeddings",
    description="Splits text into overlapping chunks, generates dense embeddings, and idempotently stores in pgvector."
)
async def index_document_version(request: IndexVersionRequest) -> IndexVersionResponse:
    try:
        response = indexer_service.index_version(request)
        return response
    except EmptyInputError as e:
        logger.warning("Empty content rejected for indexing: %s", e)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "EmptyInputError", "message": str(e)}
        )
    except IndexingError as e:
        logger.error("Indexing failed for version %s: %s", request.version_id, e)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail={"error": "IndexingError", "message": str(e)}
        )
    except Exception as e:
        logger.error("Unexpected error indexing version %s: %s", request.version_id, e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "IndexingServerError", "message": f"Server failed to index version: {str(e)}"}
        )


@router.get(
    "/chunks/{version_id}",
    response_model=VersionChunksResponse,
    summary="Inspect stored chunks and embeddings for a version",
    description="Inspects active chunks, embeddings dimension, and metadata for a given DocumentVersion."
)
async def get_version_chunks(version_id: UUID, include_embeddings: bool = False) -> VersionChunksResponse:
    chunks = indexer_service.get_version_chunks(version_id)
    if not chunks:
        # Check if version exists in status
        status_info = indexer_service.get_status(version_id)
        doc_id = status_info["document_id"] if status_info else version_id
        return VersionChunksResponse(
            version_id=version_id,
            document_id=doc_id,
            total_chunks=0,
            chunks=[]
        )

    inspect_items = []
    for c in chunks:
        emb_sample = c.embedding[:5] if c.embedding else []
        emb_dim = len(c.embedding) if c.embedding else 0
        full_emb = c.embedding if (include_embeddings and c.embedding) else None
        inspect_items.append(ChunkInspectResponse(
            id=c.id,
            document_id=c.document_id,
            version_id=c.version_id,
            chunk_index=c.chunk_index,
            chunk_text=c.chunk_text,
            page_number=c.page_number,
            source_title=c.source_title,
            embedding_dimension=emb_dim,
            embedding_sample=emb_sample,
            embedding=full_emb,
            metadata=c.metadata
        ))

    return VersionChunksResponse(
        version_id=version_id,
        document_id=chunks[0].document_id,
        total_chunks=len(inspect_items),
        chunks=inspect_items
    )


@router.get(
    "/indexing-status/{version_id}",
    response_model=IndexingStatusResponse,
    summary="Retrieve indexing status for a document version"
)
async def get_indexing_status(version_id: UUID) -> IndexingStatusResponse:
    status_info = indexer_service.get_status(version_id)
    if not status_info:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail={"error": "NotFound", "message": f"No indexing record found for version {version_id}"}
        )

    return IndexingStatusResponse(
        version_id=status_info["version_id"],
        document_id=status_info["document_id"],
        status=status_info["status"],
        chunk_count=status_info["chunk_count"],
        embedding_model=status_info["embedding_model"],
        embedding_dimension=status_info["embedding_dimension"],
        error_message=status_info.get("error_message"),
        updated_at=status_info.get("updated_at")
    )
