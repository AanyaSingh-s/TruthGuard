"""Health check endpoints for TruthGuard AI Service."""

import logging
from fastapi import APIRouter
from app.config import settings
from app.schemas.health import HealthResponse

logger = logging.getLogger(__name__)

router = APIRouter(tags=["Health"])


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="Get AI Service Health Status",
    description="Returns service availability, version, environment, and subsystem states."
)
async def get_health() -> HealthResponse:
    logger.debug("Received AI Service health check query")
    return HealthResponse(
        status="UP",
        service=settings.app_name,
        version=settings.app_version,
        environment=settings.environment,
        details={
            "role": "Adversarial verification, claim extraction, and embedding generation",
            "models_ready": "mock_mode_active",
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
