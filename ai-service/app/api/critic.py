"""TruthGuard Critic/Adversary Agent API endpoints."""

import logging
from fastapi import APIRouter, HTTPException, status

from app.schemas.critic import CriticRequest, CriticResponse
from app.services.critic import CriticService
from app.services.llm import (
    LLMProviderError,
    LLMTimeoutError,
    MalformedLLMResponseError,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1", tags=["Adversarial Verification"])

# Singleton critic service instance
critic_service = CriticService()


@router.post(
    "/critique",
    response_model=CriticResponse,
    summary="Critique candidate answer for contradictions, unsupported claims, and staleness",
    description="Independently verifies candidate answers against evidence passages, detecting contradictions, scope misalignment, and ungrounded assertions."
)
async def critique_candidate_answer(request: CriticRequest) -> CriticResponse:
    try:
        return critic_service.critique(request)
    except ValueError as e:
        logger.warning("Invalid critique request: %s", e)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "InvalidRequestError", "message": str(e)}
        )
    except MalformedLLMResponseError as e:
        logger.error("Malformed Critic LLM response: %s", e)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail={"error": "MalformedLLMResponseError", "message": str(e)}
        )
    except LLMTimeoutError as e:
        logger.error("Critic LLM timeout: %s", e)
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail={"error": "LLMTimeoutError", "message": str(e)}
        )
    except LLMProviderError as e:
        logger.error("Critic LLM provider failure: %s", e)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail={"error": "LLMProviderError", "message": str(e)}
        )
    except Exception as e:
        logger.error("Unexpected Critic server error: %s", e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "CriticServerError", "message": f"Server failed during critique: {str(e)}"}
        )
