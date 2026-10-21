"""Integration tests for TruthGuard end-to-end AI pipeline endpoint."""

from unittest.mock import MagicMock
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.schemas.critic import CriticResponse, CriticVerdict
from app.schemas.retrieval import RetrievalResponse, RetrievedPassage
from app.schemas.synthesis import AtomicClaim, ClaimType, SynthesisResponse
from app.services.llm import LLMProviderError, LLMTimeoutError

client = TestClient(app)


def test_pipeline_analyze_success(monkeypatch):
    chunk_id = uuid4()
    doc_id = uuid4()
    version_id = uuid4()

    mock_passage = RetrievedPassage(
        chunk_id=chunk_id,
        document_id=doc_id,
        version_id=version_id,
        version_identifier="v1.0",
        chunk_index=0,
        text="The server session timeout is 60 minutes.",
        score=0.92,
        source_title="Access Policy"
    )

    mock_retrieval_res = RetrievalResponse(
        question="What is the session timeout?",
        total_retrieved=1,
        passages=[mock_passage]
    )

    mock_synth_res = SynthesisResponse(
        question="What is the session timeout?",
        candidate_answer="The server session timeout is 60 minutes.",
        claims=[
            AtomicClaim(
                claim_id="c1",
                text="The server session timeout is 60 minutes.",
                claim_type=ClaimType.FACT,
                cited_chunk_ids=[chunk_id],
                valid_chunk_ids=[chunk_id],
                invalid_chunk_ids=[],
                is_grounded=True
            )
        ],
        total_claims=1,
        supported_claims_count=1,
        unsupported_claims_count=0,
        is_fully_grounded=True,
        uncertainty_notes=[],
        missing_information=[],
        evidence_chunk_ids=[chunk_id]
    )

    mock_critic_res = CriticResponse(
        verdict=CriticVerdict.PASS,
        confidence=0.98,
        conflicting_claim_ids=[],
        conflicting_evidence_ids=[],
        unsupported_claim_ids=[],
        source_positions=[],
        staleness_findings=[],
        contradictions=[],
        explanation="Candidate answer is fully substantiated."
    )

    from app.api.pipeline import pipeline_service
    monkeypatch.setattr(pipeline_service.retriever, "retrieve", MagicMock(return_value=mock_retrieval_res))
    monkeypatch.setattr(pipeline_service.synthesizer, "synthesize", MagicMock(return_value=mock_synth_res))
    monkeypatch.setattr(pipeline_service.critic, "critique", MagicMock(return_value=mock_critic_res))

    response = client.post(
        "/api/v1/pipeline/analyze",
        json={"question": "What is the session timeout?", "correlation_id": "test-corr-123"},
        headers={"X-Correlation-ID": "test-corr-123"}
    )

    assert response.status_code == 200
    data = response.json()
    assert data["question"] == "What is the session timeout?"
    assert data["correlation_id"] == "test-corr-123"
    assert len(data["passages"]) == 1
    assert data["synthesizer_output"]["candidate_answer"] == "The server session timeout is 60 minutes."
    assert data["critic_output"]["verdict"] == "PASS"
    assert "stages" in data["trace"]
    assert "retrieval" in data["trace"]["stages"]
    assert "synthesis" in data["trace"]["stages"]
    assert "critic" in data["trace"]["stages"]
    assert data["trace"]["stages"]["retrieval"]["passages_count"] == 1


def test_pipeline_analyze_timeout_returns_504(monkeypatch):
    from app.api.pipeline import pipeline_service
    monkeypatch.setattr(
        pipeline_service.synthesizer,
        "synthesize",
        MagicMock(side_effect=LLMTimeoutError("LLM call timed out after 30.0s"))
    )

    response = client.post(
        "/api/v1/pipeline/analyze",
        json={"question": "Timeout query"}
    )
    assert response.status_code == 504
    assert response.json()["detail"]["error"] == "LLMTimeoutError"


def test_pipeline_analyze_provider_error_returns_502(monkeypatch):
    from app.api.pipeline import pipeline_service
    monkeypatch.setattr(
        pipeline_service.synthesizer,
        "synthesize",
        MagicMock(side_effect=LLMProviderError("Provider unavailable"))
    )

    response = client.post(
        "/api/v1/pipeline/analyze",
        json={"question": "Provider error query"}
    )
    assert response.status_code == 502
    assert response.json()["detail"]["error"] == "LLMProviderError"


def test_pipeline_analyze_empty_question_rejected():
    response = client.post(
        "/api/v1/pipeline/analyze",
        json={"question": ""}
    )
    assert response.status_code == 422
