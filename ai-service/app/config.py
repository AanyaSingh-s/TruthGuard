"""Configuration settings for TruthGuard AI Service."""

import os
from pydantic import BaseModel, Field


class Settings(BaseModel):
    app_name: str = "truthguard-ai-service"
    app_version: str = "0.1.0"
    environment: str = Field(default_factory=lambda: os.getenv("AI_SERVICE_ENV", "development"))
    host: str = Field(default_factory=lambda: os.getenv("AI_SERVICE_HOST", "0.0.0.0"))
    port: int = Field(default_factory=lambda: int(os.getenv("AI_SERVICE_PORT", "8000")))
    cors_origins: list[str] = [
        "http://localhost:3000",
        "http://localhost:5173",
        "http://localhost:8080",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:5173",
        "http://127.0.0.1:8080",
    ]


settings = Settings()
