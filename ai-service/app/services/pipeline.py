"""Integrated TruthGuard AI verification pipeline orchestrator."""

import logging
import time
from typing import Any, Dict

from app.schemas.pipeline import PipelineAnalysisRequest, PipelineAnalysisResponse
from app.schemas.retrieval import RetrievalRequest
from app.schemas.synthesis import SynthesisRequest
from app.schemas.critic import CriticRequest
from app.services.retriever import RetrieverService
from app.services.synthesizer import SynthesizerService
from app.services.critic import CriticService

logger = logging.getLogger(__name__)


class PipelineService:
    """Orchestrates retrieval, candidate synthesis, and adversarial critique."""

    def __init__(
        self,
        retriever_service: RetrieverService = None,
        synthesizer_service: SynthesizerService = None,
        critic_service: CriticService = None
    ):
        self.retriever = retriever_service or RetrieverService()
        self.synthesizer = synthesizer_service or SynthesizerService()
        self.critic = critic_service or CriticService()

    def analyze(self, request: PipelineAnalysisRequest) -> PipelineAnalysisResponse:
        """Executes the full AI pipeline: Retrieve -> Synthesize -> Critique."""
        start_time = time.perf_counter()
        correlation_id = request.correlation_id or "untracked"
        logger.info(
            "[%s] Starting AI pipeline analysis for query: '%s'",
            correlation_id, request.question
        )

        trace: Dict[str, Any] = {
            "correlation_id": correlation_id,
            "stages": {}
        }

        # -----------------------------------------------------------------
        # Stage 1: Evidence Retrieval
        # -----------------------------------------------------------------
        t0 = time.perf_counter()
        retrieval_req = RetrievalRequest(
            question=request.question,
            filters=request.filters,
            top_k=request.top_k,
            min_score=request.min_score,
            semantic_weight=request.semantic_weight,
            ranking_method=request.ranking_method
        )
        retrieval_res = self.retriever.retrieve(retrieval_req)
        retrieval_duration_ms = int((time.perf_counter() - t0) * 1000)
        trace["stages"]["retrieval"] = {
            "status": "COMPLETED",
            "duration_ms": retrieval_duration_ms,
            "passages_count": len(retrieval_res.passages)
        }
        logger.info(
            "[%s] Stage 1 (Retrieval) finished in %d ms (%d passages)",
            correlation_id, retrieval_duration_ms, len(retrieval_res.passages)
        )

        # -----------------------------------------------------------------
        # Stage 2: Candidate Answer Synthesis
        # -----------------------------------------------------------------
        t1 = time.perf_counter()
        synth_req = SynthesisRequest(
            question=request.question,
            passages=retrieval_res.passages,
            temperature=request.temperature,
            max_tokens=request.max_tokens
        )
        synth_res = self.synthesizer.synthesize(synth_req)
        synthesis_duration_ms = int((time.perf_counter() - t1) * 1000)
        trace["stages"]["synthesis"] = {
            "status": "COMPLETED",
            "duration_ms": synthesis_duration_ms,
            "claims_count": len(synth_res.claims),
            "fully_grounded": synth_res.is_fully_grounded
        }
        logger.info(
            "[%s] Stage 2 (Synthesis) finished in %d ms (%d claims, grounded=%s)",
            correlation_id, synthesis_duration_ms, len(synth_res.claims), synth_res.is_fully_grounded
        )

        # -----------------------------------------------------------------
        # Stage 3: Adversarial Verification (Critic)
        # -----------------------------------------------------------------
        t2 = time.perf_counter()
        critic_req = CriticRequest(
            question=request.question,
            candidate_answer=synth_res.candidate_answer,
            claims=synth_res.claims,
            passages=retrieval_res.passages
        )
        critic_res = self.critic.critique(critic_req)
        critic_duration_ms = int((time.perf_counter() - t2) * 1000)
        trace["stages"]["critic"] = {
            "status": "COMPLETED",
            "duration_ms": critic_duration_ms,
            "verdict": critic_res.verdict.value,
            "confidence": critic_res.confidence,
            "contradictions_count": len(critic_res.contradictions),
            "unsupported_claims_count": len(critic_res.unsupported_claim_ids)
        }
        logger.info(
            "[%s] Stage 3 (Critic) finished in %d ms (verdict=%s, contradictions=%d)",
            correlation_id, critic_duration_ms, critic_res.verdict.value, len(critic_res.contradictions)
        )

        total_duration_ms = int((time.perf_counter() - start_time) * 1000)
        trace["total_duration_ms"] = total_duration_ms

        logger.info(
            "[%s] AI pipeline completed in %d ms total",
            correlation_id, total_duration_ms
        )

        return PipelineAnalysisResponse(
            question=request.question,
            correlation_id=request.correlation_id,
            passages=retrieval_res.passages,
            synthesizer_output=synth_res,
            critic_output=critic_res,
            trace=trace
        )
