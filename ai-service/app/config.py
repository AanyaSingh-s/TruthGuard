"""Configuration settings for TruthGuard AI Service."""

import os
from typing import Optional
from pydantic import BaseModel, Field


class Settings(BaseModel):
    app_name: str = "truthguard-ai-service"
    app_version: str = "0.2.0"
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

    # Database connection parameters
    db_host: str = Field(default_factory=lambda: os.getenv("DB_HOST", "localhost"))
    db_port: int = Field(default_factory=lambda: int(os.getenv("DB_PORT", "5432")))
    db_name: str = Field(default_factory=lambda: os.getenv("DB_NAME", "truthguard"))
    db_user: str = Field(default_factory=lambda: os.getenv("DB_USER", "truthguard"))
    db_password: str = Field(default_factory=lambda: os.getenv("DB_PASSWORD", "truthguard_dev_password"))
    database_url: str = Field(default_factory=lambda: os.getenv(
        "DATABASE_URL",
        f"postgresql://{os.getenv('DB_USER', 'truthguard')}:{os.getenv('DB_PASSWORD', 'truthguard_dev_password')}@{os.getenv('DB_HOST', 'localhost')}:{os.getenv('DB_PORT', '5432')}/{os.getenv('DB_NAME', 'truthguard')}"
    ))

    # Embedding model configuration
    embedding_model_name: str = Field(
        default_factory=lambda: os.getenv("EMBEDDING_MODEL_NAME", "sentence-transformers/all-MiniLM-L6-v2")
    )
    embedding_dimension: int = Field(
        default_factory=lambda: int(os.getenv("EMBEDDING_DIMENSION", "384"))
    )
    embeddings_fallback: bool = Field(
        default_factory=lambda: os.getenv("EMBEDDINGS_FALLBACK", "false").lower() in ("true", "1")
    )

    # Chunking configuration
    default_chunk_size: int = Field(
        default_factory=lambda: int(os.getenv("DEFAULT_CHUNK_SIZE", "500"))
    )
    default_chunk_overlap: int = Field(
        default_factory=lambda: int(os.getenv("DEFAULT_CHUNK_OVERLAP", "100"))
    )

    # LLM Provider configuration
    # Supported: gemini | openai | mock
    llm_provider: str = Field(
        default_factory=lambda: os.getenv("LLM_PROVIDER", "").lower().strip()
    )
    llm_model: str = Field(
        default_factory=lambda: os.getenv("LLM_MODEL", "").strip()
    )
    llm_api_key: Optional[str] = Field(
        default_factory=lambda: os.getenv("LLM_API_KEY")
    )
    gemini_api_key: Optional[str] = Field(
        default_factory=lambda: os.getenv("GEMINI_API_KEY")
    )
    openai_api_key: Optional[str] = Field(
        default_factory=lambda: os.getenv("OPENAI_API_KEY")
    )
    openai_base_url: str = Field(
        default_factory=lambda: os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    )
    gemini_model: str = Field(
        default_factory=lambda: os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
    )
    openai_model: str = Field(
        default_factory=lambda: os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    )
    llm_timeout_seconds: int = Field(
        default_factory=lambda: int(os.getenv("LLM_TIMEOUT_SECONDS", "30"))
    )
    llm_temperature: float = Field(
        default_factory=lambda: float(os.getenv("LLM_TEMPERATURE", "0.0"))
    )
    llm_max_tokens: int = Field(
        default_factory=lambda: int(os.getenv("LLM_MAX_TOKENS", "1024"))
    )


settings = Settings()
