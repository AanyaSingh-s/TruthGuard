"""TruthGuard Retriever Agent API endpoints."""

import logging
from fastapi import APIRouter, HTTPException, status

from app.api.indexing import indexer_service
from app.schemas.retrieval import RetrievalRequest, RetrievalResponse
from app.services.retriever import RetrieverService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1", tags=["RAG Evidence Retrieval"])

# Singleton service instance sharing store with indexer
retriever_service = RetrieverService(store=indexer_service.store)


@router.post(
    "/retrieve",
    response_model=RetrievalResponse,
    summary="Retrieve ranked evidence passages with provenance",
    description="Embeds the question, executes hybrid semantic + keyword retrieval, and returns ranked passages with full version provenance."
)
async def retrieve_evidence(request: RetrievalRequest) -> RetrievalResponse:
    try:
        response = retriever_service.retrieve(request)
        return response
    except ValueError as e:
        logger.warning("Invalid retrieval request: %s", e)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "InvalidQueryError", "message": str(e)}
        )
    except Exception as e:
        logger.error("Error executing evidence retrieval: %s", e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "RetrievalServerError", "message": f"Retriever service error: {str(e)}"}
        )
