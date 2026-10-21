"""Health check endpoints for TruthGuard AI Service."""

import logging
from fastapi import APIRouter
from app.config import settings
from app.schemas.health import HealthResponse
from app.services.llm import resolve_provider_type

logger = logging.getLogger(__name__)

router = APIRouter(tags=["Health"])


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="Get AI Service Health Status",
    description="Returns service availability, version, environment, subsystem states, and active LLM provider."
)
async def get_health() -> HealthResponse:
    logger.debug("Received AI Service health check query")
    p_type, m_name, _ = resolve_provider_type()

    return HealthResponse(
        status="UP",
        service=settings.app_name,
        version=settings.app_version,
        environment=settings.environment,
        llm_provider=p_type,
        llm_model=m_name,
        details={
            "role": "Adversarial verification, claim extraction, and embedding generation",
            "llm_provider": p_type,
            "llm_model": m_name,
            "embedding_model": settings.embedding_model_name,
            "embedding_dimension": str(settings.embedding_dimension),
            "vector_search_ready": "configured"
        }
    )


@router.get(
    "/api/v1/health",
    response_model=HealthResponse,
    include_in_schema=False
)
async def get_v1_health() -> HealthResponse:
    return await get_health()
