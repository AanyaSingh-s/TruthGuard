"""Pydantic schemas for the integrated TruthGuard AI pipeline."""

from typing import Any, Dict, List, Optional
from uuid import UUID
from pydantic import BaseModel, Field

from app.schemas.retrieval import RetrievalFilter, RetrievedPassage
from app.schemas.synthesis import SynthesisResponse
from app.schemas.critic import CriticResponse


class PipelineAnalysisRequest(BaseModel):
    """Request payload to execute the full AI verification pipeline."""
    question: str = Field(..., min_length=1, description="Question or assertion query to evaluate")
    filters: Optional[RetrievalFilter] = Field(default=None, description="Optional metadata or document/version filters")
    top_k: int = Field(default=5, ge=1, le=100, description="Maximum number of evidence chunks to retrieve")
    min_score: float = Field(default=0.0, ge=0.0, le=1.0, description="Minimum combined relevance threshold")
    semantic_weight: float = Field(default=0.7, ge=0.0, le=1.0, description="Weight alpha for dense retrieval")
    ranking_method: str = Field(default="hybrid_linear", description="Ranking strategy: 'hybrid_linear' or 'rrf'")
    temperature: float = Field(default=0.0, ge=0.0, le=1.0, description="Sampling temperature for answer synthesis")
    max_tokens: int = Field(default=1024, ge=64, le=4096, description="Maximum token limit for synthesized response")
    correlation_id: Optional[str] = Field(default=None, description="Correlation identifier for tracing across services")


class PipelineAnalysisResponse(BaseModel):
    """Unified response containing retrieved evidence passages, synthesizer answer, and critic review."""
    question: str = Field(..., description="The query evaluated")
    correlation_id: Optional[str] = Field(default=None, description="Correlation tracking identifier")
    passages: List[RetrievedPassage] = Field(default_factory=list, description="Ranked evidence passages retrieved")
    synthesizer_output: SynthesisResponse = Field(..., description="Synthesizer candidate answer with atomic claims")
    critic_output: CriticResponse = Field(..., description="Adversarial critique findings and contradictions")
    trace: Dict[str, Any] = Field(default_factory=dict, description="Stage timings and execution metadata")
