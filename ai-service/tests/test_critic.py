"""Unit and API integration tests for TruthGuard Critic/Adversary Agent."""

import json
import uuid
import pytest
from fastapi.testclient import TestClient

from app.api.critic import critic_service
from app.main import app
from app.schemas.critic import CriticRequest, CriticVerdict
from app.schemas.retrieval import RetrievedPassage
from app.schemas.synthesis import AtomicClaim, ClaimType
from app.services.critic import CriticService
from app.services.llm import MockLLMProvider

client = TestClient(app)


def make_passage(
    chunk_id: uuid.UUID,
    text: str,
    version_id: uuid.UUID,
    version_identifier: str = "v1.0",
    effective_date: str = "2026-01-01",
    authority: str = "OFFICIAL",
    supersedes: uuid.UUID = None,
    superseded_by: uuid.UUID = None
) -> RetrievedPassage:
    return RetrievedPassage(
        chunk_id=chunk_id,
        document_id=uuid.uuid4(),
        version_id=version_id,
        version_identifier=version_identifier,
        chunk_index=0,
        text=text,
        score=0.9,
        source_title="Access Policy",
        source_type="POLICY",
        page_number=1,
        effective_date=effective_date,
        authority_level=authority,
        supersedes_version_id=supersedes,
        superseded_by_id=superseded_by
    )


# ============================================================================
# 1. Agreement / Consistent Evidence -> PASS
# ============================================================================

def test_critic_verdict_pass_on_consistent_evidence():
    """Verify consistent evidence with grounded candidate answer yields PASS verdict."""
    c1 = uuid.uuid4()
    v1 = uuid.uuid4()
    passages = [make_passage(c1, "Tokens must expire after 15 minutes of inactivity.", v1)]

    claims = [
        AtomicClaim(
            claim_id="c-1",
            text="Tokens expire after 15 minutes of inactivity.",
            claim_type=ClaimType.FACT,
            cited_chunk_ids=[c1],
            valid_chunk_ids=[c1],
            is_grounded=True
        )
    ]

    llm_response = {
        "verdict": "PASS",
        "confidence": 0.95,
        "contradictions": [],
        "unsupported_claim_ids": [],
        "explanation": "Evidence is consistent and candidate answer is fully supported.",
        "uncertainty_notes": []
    }

    provider = MockLLMProvider(custom_response=json.dumps(llm_response))
    critic = CriticService(provider=provider)

    res = critic.critique(CriticRequest(
        question="What is the token expiration period?",
        candidate_answer="Tokens expire after 15 minutes of inactivity.",
        claims=claims,
        passages=passages
    ))

    assert res.verdict == CriticVerdict.PASS
    assert res.confidence >= 0.9
    assert len(res.contradictions) == 0
    assert len(res.unsupported_claim_ids) == 0


# ============================================================================
# 2. Direct Contradictions -> CONFLICT
# ============================================================================

def test_critic_verdict_conflict_on_contradictory_evidence():
    """Verify direct factual contradiction between passages yields CONFLICT verdict."""
    c1, v1 = uuid.uuid4(), uuid.uuid4()
    c2, v2 = uuid.uuid4(), uuid.uuid4()

    passages = [
        make_passage(c1, "Maximum session timeout is 30 minutes for all administrative portals.", v1, "v1.0", "2024-01-01"),
        make_passage(c2, "Maximum session timeout is 60 minutes for all administrative portals.", v2, "v2.0", "2025-01-01")
    ]

    claims = [
        AtomicClaim(
            claim_id="c-1",
            text="The maximum session timeout is 30 minutes for administrative portals.",
            claim_type=ClaimType.FACT,
            cited_chunk_ids=[c1],
            valid_chunk_ids=[c1],
            is_grounded=True
        )
    ]

    llm_response = {
        "verdict": "CONFLICT",
        "confidence": 0.95,
        "contradictions": [
            {
                "topic": "Session timeout duration",
                "conflicting_claim_ids": ["c-1"],
                "conflicting_evidence_ids": [str(c1), str(c2)],
                "scope_aligned": True,
                "positions": [
                    {
                        "passage_id": str(c1),
                        "source_title": "Access Policy",
                        "version_identifier": "v1.0",
                        "quotation": "Maximum session timeout is 30 minutes",
                        "assertion": "Session timeout is 30 minutes"
                    },
                    {
                        "passage_id": str(c2),
                        "source_title": "Access Policy",
                        "version_identifier": "v2.0",
                        "quotation": "Maximum session timeout is 60 minutes",
                        "assertion": "Session timeout is 60 minutes"
                    }
                ],
                "explanation": "Direct contradiction between v1.0 (30m) and v2.0 (60m) for administrative portals."
            }
        ],
        "unsupported_claim_ids": [],
        "explanation": "Direct contradiction detected regarding session timeout duration.",
        "uncertainty_notes": ["Multiple active conflicting policies detected."]
    }

    provider = MockLLMProvider(custom_response=json.dumps(llm_response))
    critic = CriticService(provider=provider)

    res = critic.critique(CriticRequest(
        question="What is the session timeout for administrative portals?",
        candidate_answer="The session timeout is 30 minutes.",
        claims=claims,
        passages=passages
    ))

    assert res.verdict == CriticVerdict.CONFLICT
    assert len(res.contradictions) == 1
    assert res.contradictions[0].scope_aligned is True
    assert c1 in res.conflicting_evidence_ids
    assert c2 in res.conflicting_evidence_ids
    assert len(res.source_positions) == 2


# ============================================================================
# 3. Deterministic Staleness and Supersession Findings
# ============================================================================

def test_critic_deterministic_supersession_findings():
    """Verify staleness findings are evaluated strictly from metadata without LLM hallucination."""
    c1, v1 = uuid.uuid4(), uuid.uuid4()
    c2, v2 = uuid.uuid4(), uuid.uuid4()

    passages = [
        make_passage(c1, "Old standard: 8 char passwords.", v1, "v1.0", "2024-01-01", superseded_by=v2),
        make_passage(c2, "New standard: 16 char passwords.", v2, "v2.0", "2025-01-01", supersedes=v1)
    ]

    dummy_response = json.dumps({
        "verdict": "PASS",
        "confidence": 0.95,
        "contradictions": [],
        "unsupported_claim_ids": [],
        "explanation": "Evidence evaluated.",
        "uncertainty_notes": []
    })
    critic = CriticService(provider=MockLLMProvider(custom_response=dummy_response))

    res = critic.critique(CriticRequest(
        question="What is password length?",
        candidate_answer="Password length is 16 characters.",
        claims=[AtomicClaim(claim_id="c-1", text="16 characters", cited_chunk_ids=[c2], valid_chunk_ids=[c2], is_grounded=True)],
        passages=passages
    ))

    # Verify deterministic metadata supersession was detected
    assert len(res.staleness_findings) == 1
    finding = res.staleness_findings[0]
    assert finding.has_explicit_supersession is True
    assert finding.older_version == "v1.0"
    assert finding.newer_version == "v2.0"
    assert finding.older_version_id == v1
    assert finding.newer_version_id == v2


# ============================================================================
# 4. Unsupported Claims Detection
# ============================================================================

def test_critic_verdict_unsupported_on_hallucinated_citations():
    """Verify claims with invalid or missing citations trigger UNSUPPORTED verdict."""
    c1, v1 = uuid.uuid4(), uuid.uuid4()
    bogus_id = uuid.uuid4()

    passages = [make_passage(c1, "Valid text.", v1)]

    claims = [
        AtomicClaim(
            claim_id="c-unsupported",
            text="Bogus claim citing non-existent chunk.",
            claim_type=ClaimType.FACT,
            cited_chunk_ids=[bogus_id],
            valid_chunk_ids=[],
            invalid_chunk_ids=[str(bogus_id)],
            is_grounded=False
        )
    ]

    dummy_response = json.dumps({
        "verdict": "PASS",
        "confidence": 0.95,
        "contradictions": [],
        "unsupported_claim_ids": [],
        "explanation": "Evidence evaluated.",
        "uncertainty_notes": []
    })
    critic = CriticService(provider=MockLLMProvider(custom_response=dummy_response))

    res = critic.critique(CriticRequest(
        question="Question?",
        candidate_answer="Answer with unsupported claim.",
        claims=claims,
        passages=passages
    ))

    assert res.verdict == CriticVerdict.UNSUPPORTED
    assert "c-unsupported" in res.unsupported_claim_ids


# ============================================================================
# 5. Insufficient Evidence Detection
# ============================================================================

def test_critic_verdict_insufficient_evidence_on_empty_passages():
    """Verify empty retrieval context deterministically yields INSUFFICIENT_EVIDENCE."""
    critic = CriticService(provider=MockLLMProvider(simulate_error=True))

    res = critic.critique(CriticRequest(
        question="What is the quantum encryption key length?",
        candidate_answer="Quantum encryption requires 512-bit keys.",
        claims=[AtomicClaim(claim_id="c-1", text="512-bit keys", cited_chunk_ids=[], is_grounded=False)],
        passages=[]  # Empty passages
    ))

    assert res.verdict == CriticVerdict.INSUFFICIENT_EVIDENCE
    assert res.confidence == 1.0


# ============================================================================
# 6. FastAPI HTTP Endpoint Test
# ============================================================================

def test_api_critique_endpoint_success():
    """POST /api/v1/critique successfully returns structured critique response."""
    c1 = str(uuid.uuid4())
    v1 = str(uuid.uuid4())

    payload = {
        "question": "What is the token expiration period?",
        "candidate_answer": "Tokens expire after 15 minutes.",
        "claims": [
            {
                "claim_id": "c-1",
                "text": "Tokens expire after 15 minutes.",
                "claim_type": "FACT",
                "cited_chunk_ids": [c1],
                "valid_chunk_ids": [c1],
                "is_grounded": True
            }
        ],
        "passages": [
            {
                "chunk_id": c1,
                "document_id": str(uuid.uuid4()),
                "version_id": v1,
                "chunk_index": 0,
                "text": "Tokens must expire after 15 minutes.",
                "score": 0.9,
                "source_title": "IAM Policy"
            }
        ]
    }

    mock_llm_json = {
        "verdict": "PASS",
        "confidence": 0.95,
        "contradictions": [],
        "unsupported_claim_ids": [],
        "explanation": "Candidate answer is verified against evidence.",
        "uncertainty_notes": []
    }
    critic_service.provider = MockLLMProvider(custom_response=json.dumps(mock_llm_json))

    res = client.post("/api/v1/critique", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["verdict"] == "PASS"
    assert data["confidence"] >= 0.9
