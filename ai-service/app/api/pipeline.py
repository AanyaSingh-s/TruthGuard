"""TruthGuard Integrated AI Pipeline API endpoints."""

import logging
from typing import Optional
from fastapi import APIRouter, Header, HTTPException, status

from app.schemas.pipeline import PipelineAnalysisRequest, PipelineAnalysisResponse
from app.services.llm import (
    LLMProviderError,
    LLMTimeoutError,
    MalformedLLMResponseError,
)
from app.services.pipeline import PipelineService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/pipeline", tags=["Pipeline Orchestration"])

pipeline_service = PipelineService()


@router.post(
    "/analyze",
    response_model=PipelineAnalysisResponse,
    summary="Execute end-to-end AI verification pipeline (Retrieval -> Synthesis -> Critique)",
    description="Retrieves evidence passages, generates grounded candidate answer with atomic claims, and conducts adversarial verification."
)
async def analyze_pipeline(
    request: PipelineAnalysisRequest,
    x_correlation_id: Optional[str] = Header(None, alias="X-Correlation-ID")
) -> PipelineAnalysisResponse:
    # Favor header correlation ID if provided, otherwise request body
    if x_correlation_id and not request.correlation_id:
        request.correlation_id = x_correlation_id

    try:
        return pipeline_service.analyze(request)
    except ValueError as e:
        logger.warning("[%s] Invalid pipeline analysis request: %s", request.correlation_id, e)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "InvalidRequestError", "message": str(e), "correlation_id": request.correlation_id}
        )
    except MalformedLLMResponseError as e:
        logger.error("[%s] Malformed LLM JSON response during pipeline analysis: %s", request.correlation_id, e)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail={"error": "MalformedLLMResponseError", "message": str(e), "correlation_id": request.correlation_id}
        )
    except LLMTimeoutError as e:
        logger.error("[%s] LLM provider timeout during pipeline analysis: %s", request.correlation_id, e)
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail={"error": "LLMTimeoutError", "message": str(e), "correlation_id": request.correlation_id}
        )
    except LLMProviderError as e:
        logger.error("[%s] LLM provider failure during pipeline analysis: %s", request.correlation_id, e)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail={"error": "LLMProviderError", "message": str(e), "correlation_id": request.correlation_id}
        )
    except Exception as e:
        logger.error("[%s] Unexpected pipeline analysis error: %s", request.correlation_id, e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "PipelineServerError", "message": f"Server failed during pipeline analysis: {str(e)}", "correlation_id": request.correlation_id}
        )
