"""Typed Health check response models."""

from datetime import datetime, timezone
from typing import Dict
from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str = Field(default="UP", description="Overall health status of AI service")
    service: str = Field(default="truthguard-ai-service", description="Service identifier")
    version: str = Field(default="0.1.0", description="Semantic version of service")
    environment: str = Field(..., description="Running environment (development, staging, production)")
    timestamp: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="UTC timestamp of the health check"
    )
    details: Dict[str, str] = Field(
        default_factory=dict,
        description="Sub-system readiness and component capabilities"
    )
