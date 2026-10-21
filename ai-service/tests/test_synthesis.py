"""Unit and API integration tests for TruthGuard Answer Synthesizer Agent."""

import json
import uuid
import pytest
from fastapi.testclient import TestClient

from app.api.retrieval import retriever_service
from app.api.synthesis import synthesizer_service
from app.main import app
from app.schemas.indexing import IndexVersionRequest
from app.schemas.retrieval import RetrievedPassage
from app.schemas.synthesis import AtomicClaim, ClaimType, SynthesisRequest
from app.services.indexer import IndexingService
from app.services.llm import (
    LLMProviderError,
    LLMTimeoutError,
    MalformedLLMResponseError,
    MockLLMProvider,
)
from app.services.synthesizer import SynthesizerService

client = TestClient(app)


# ============================================================================
# Helper fixtures & data
# ============================================================================

def make_sample_passage(chunk_id: uuid.UUID, text: str, version: str = "v1.0") -> RetrievedPassage:
    return RetrievedPassage(
        chunk_id=chunk_id,
        document_id=uuid.uuid4(),
        version_id=uuid.uuid4(),
        version_identifier=version,
        chunk_index=0,
        text=text,
        score=0.95,
        semantic_score=0.92,
        keyword_score=0.98,
        source_title="Security Policy",
        source_type="POLICY",
        page_number=1,
        effective_date="2026-01-01",
        upload_timestamp="2026-01-01T00:00:00Z",
        authority_level="OFFICIAL"
    )


# ============================================================================
# 1. Grounded Answer Synthesis & Citation Verification
# ============================================================================

def test_grounded_answer_synthesis_with_valid_citations():
    """Verify well-formed candidate answer with valid chunk citations passes validation as fully grounded."""
    c1_id = uuid.uuid4()
    c2_id = uuid.uuid4()
    passages = [
        make_sample_passage(c1_id, "Session tokens must expire after 15 minutes of inactivity."),
        make_sample_passage(c2_id, "Administrative access requires FIDO2 hardware security keys.")
    ]

    llm_payload = {
        "candidate_answer": "Session tokens expire after 15 minutes of inactivity, and administrative access requires FIDO2 hardware keys.",
        "claims": [
            {
                "claim_id": "c-1",
                "text": "Session tokens expire after 15 minutes of inactivity.",
                "claim_type": "FACT",
                "supporting_chunk_ids": [str(c1_id)]
            },
            {
                "claim_id": "c-2",
                "text": "Administrative access requires FIDO2 hardware security keys.",
                "claim_type": "FACT",
                "supporting_chunk_ids": [str(c2_id)]
            }
        ],
        "uncertainty_notes": [],
        "missing_information": []
    }

    provider = MockLLMProvider(custom_response=json.dumps(llm_payload))
    synthesizer = SynthesizerService(provider=provider)

    req = SynthesisRequest(
        question="What are the rules for session tokens and admin access?",
        passages=passages
    )
    res = synthesizer.synthesize(req)

    assert res.total_claims == 2
    assert res.supported_claims_count == 2
    assert res.unsupported_claims_count == 0
    assert res.is_fully_grounded is True
    assert len(res.claims) == 2

    for claim in res.claims:
        assert claim.is_grounded is True
        assert len(claim.valid_chunk_ids) == 1
        assert len(claim.invalid_chunk_ids) == 0
        assert claim.unsupported_reason is None


# ============================================================================
# 2. Hallucinated / Bogus Chunk ID Detection (Critical Task 6)
# ============================================================================

def test_hallucinated_chunk_id_detected_and_flagged_as_unsupported():
    """
    CRITICAL REQUIREMENT:
    Do not accept citations merely because the model generated plausible-looking IDs.
    Hallucinated UUIDs not in the supplied evidence must be caught and flagged as unsupported.
    """
    valid_id = uuid.uuid4()
    fake_hallucinated_id = uuid.uuid4()  # Plausible UUID, but NOT in passages!

    passages = [
        make_sample_passage(valid_id, "Passwords must be at least 16 characters in length.")
    ]

    llm_payload = {
        "candidate_answer": "Passwords must be at least 16 characters and encrypted with Argon2id.",
        "claims": [
            {
                "claim_id": "c-1",
                "text": "Passwords must be at least 16 characters in length.",
                "claim_type": "FACT",
                "supporting_chunk_ids": [str(valid_id)]
            },
            {
                "claim_id": "c-2",
                "text": "Passwords must be encrypted with Argon2id.",
                "claim_type": "FACT",
                "supporting_chunk_ids": [str(fake_hallucinated_id)]  # Bogus citation!
            }
        ],
        "uncertainty_notes": [],
        "missing_information": []
    }

    provider = MockLLMProvider(custom_response=json.dumps(llm_payload))
    synthesizer = SynthesizerService(provider=provider)

    req = SynthesisRequest(
        question="What are the password requirements?",
        passages=passages
    )
    res = synthesizer.synthesize(req)

    assert res.total_claims == 2
    assert res.supported_claims_count == 1
    assert res.unsupported_claims_count == 1
    assert res.is_fully_grounded is False

    # Check valid claim
    assert res.claims[0].is_grounded is True
    assert res.claims[0].valid_chunk_ids == [valid_id]

    # Check hallucinated claim
    hallucinated_claim = res.claims[1]
    assert hallucinated_claim.is_grounded is False
    assert len(hallucinated_claim.invalid_chunk_ids) == 1
    assert str(fake_hallucinated_id) in hallucinated_claim.invalid_chunk_ids[0]
    assert "Cites unverified/hallucinated chunk IDs" in hallucinated_claim.unsupported_reason


# ============================================================================
# 3. Claim With No Citations
# ============================================================================

def test_claim_with_empty_citations_flagged_as_unsupported():
    """Verify claims without supporting citations are flagged as ungrounded."""
    valid_id = uuid.uuid4()
    passages = [make_sample_passage(valid_id, "Data must be backed up daily.")]

    llm_payload = {
        "candidate_answer": "Data must be backed up daily and sent offsite.",
        "claims": [
            {
                "claim_id": "c-1",
                "text": "Data must be backed up daily.",
                "claim_type": "FACT",
                "supporting_chunk_ids": [str(valid_id)]
            },
            {
                "claim_id": "c-2",
                "text": "Backups must be sent offsite.",
                "claim_type": "FACT",
                "supporting_chunk_ids": []  # Missing citation!
            }
        ],
        "uncertainty_notes": [],
        "missing_information": []
    }

    provider = MockLLMProvider(custom_response=json.dumps(llm_payload))
    synthesizer = SynthesizerService(provider=provider)

    res = synthesizer.synthesize(SynthesisRequest(
        question="What are the backup requirements?",
        passages=passages
    ))

    assert res.supported_claims_count == 1
    assert res.unsupported_claims_count == 1
    assert res.is_fully_grounded is False
    assert res.claims[1].is_grounded is False
    assert "No supporting evidence chunk IDs provided" in res.claims[1].unsupported_reason


# ============================================================================
# 4. Distinguish Facts, Uncertainty, and Missing Information
# ============================================================================

def test_distinguish_facts_uncertainty_and_missing_information():
    """Verify model can classify facts vs conflicts vs uncertainty, and document missing information."""
    c1_id = uuid.uuid4()
    c2_id = uuid.uuid4()
    passages = [
        make_sample_passage(c1_id, "Version 1.0: Session timeout is 30 minutes.", version="v1.0"),
        make_sample_passage(c2_id, "Version 2.0: Session timeout is 60 minutes.", version="v2.0")
    ]

    llm_payload = {
        "candidate_answer": "There is a version conflict: Version 1 specifies 30 minutes, whereas Version 2 specifies 60 minutes.",
        "claims": [
            {
                "claim_id": "c-1",
                "text": "Version 1 specifies a session timeout of 30 minutes.",
                "claim_type": "FACT",
                "supporting_chunk_ids": [str(c1_id)]
            },
            {
                "claim_id": "c-2",
                "text": "A contradiction exists between Version 1 and Version 2 regarding timeout duration.",
                "claim_type": "CONFLICT",
                "supporting_chunk_ids": [str(c1_id), str(c2_id)]
            }
        ],
        "uncertainty_notes": [
            "Contradictory session timeout rules between v1.0 and v2.0."
        ],
        "missing_information": [
            "No precedence or supersession policy provided to determine whether v2.0 is active."
        ]
    }

    provider = MockLLMProvider(custom_response=json.dumps(llm_payload))
    synthesizer = SynthesizerService(provider=provider)

    res = synthesizer.synthesize(SynthesisRequest(
        question="What is the session timeout?",
        passages=passages
    ))

    assert res.total_claims == 2
    assert res.claims[0].claim_type == ClaimType.FACT
    assert res.claims[1].claim_type == ClaimType.CONFLICT
    assert len(res.uncertainty_notes) == 1
    assert "v1.0 and v2.0" in res.uncertainty_notes[0]
    assert len(res.missing_information) == 1
    assert "precedence" in res.missing_information[0]


# ============================================================================
# 5. Malformed Markdown Code Fences Recovery
# ============================================================================

def test_malformed_json_wrapped_in_markdown_fences_recovered():
    """Verify JSON wrapped inside markdown ```json ... ``` code blocks is cleanly parsed."""
    c_id = uuid.uuid4()
    passages = [make_sample_passage(c_id, "Encryption standard: AES-256.")]

    wrapped_response = f"""
Here is the structured JSON output you requested:

```json
{{
  "candidate_answer": "Encryption standard requires AES-256.",
  "claims": [
    {{
      "claim_id": "c-1",
      "text": "Encryption standard requires AES-256.",
      "claim_type": "FACT",
      "supporting_chunk_ids": ["{str(c_id)}"]
    }}
  ],
  "uncertainty_notes": [],
  "missing_information": []
}}
```

Hope this helps!
"""
    provider = MockLLMProvider(custom_response=wrapped_response)
    synthesizer = SynthesizerService(provider=provider)

    res = synthesizer.synthesize(SynthesisRequest(
        question="What is the encryption standard?",
        passages=passages
    ))

    assert res.candidate_answer == "Encryption standard requires AES-256."
    assert res.is_fully_grounded is True


# ============================================================================
# 6. Malformed JSON Error Handling
# ============================================================================

def test_unrecoverable_malformed_json_raises_error():
    """Verify non-JSON output from LLM raises MalformedLLMResponseError."""
    passages = [make_sample_passage(uuid.uuid4(), "Sample text.")]
    provider = MockLLMProvider(simulate_malformed=True)
    synthesizer = SynthesizerService(provider=provider)

    with pytest.raises(MalformedLLMResponseError, match="Failed to find valid JSON"):
        synthesizer.synthesize(SynthesisRequest(
            question="Question?",
            passages=passages
        ))


# ============================================================================
# 7. Timeout and Provider Error Handling
# ============================================================================

def test_provider_timeout_raises_llm_timeout_error():
    """Verify provider request timeout raises LLMTimeoutError."""
    passages = [make_sample_passage(uuid.uuid4(), "Sample text.")]
    provider = MockLLMProvider(simulate_timeout=True)
    synthesizer = SynthesizerService(provider=provider)

    with pytest.raises(LLMTimeoutError, match="timeout limit"):
        synthesizer.synthesize(SynthesisRequest(
            question="Question?",
            passages=passages
        ))


def test_provider_error_raises_llm_provider_error():
    """Verify provider 503/API failure raises LLMProviderError."""
    passages = [make_sample_passage(uuid.uuid4(), "Sample text.")]
    provider = MockLLMProvider(simulate_error=True)
    synthesizer = SynthesizerService(provider=provider)

    with pytest.raises(LLMProviderError, match="Service Unavailable"):
        synthesizer.synthesize(SynthesisRequest(
            question="Question?",
            passages=passages
        ))


# ============================================================================
# 8. Zero Evidence Edge Case
# ============================================================================

def test_zero_evidence_returns_insufficient_evidence():
    """Verify zero evidence passages returns structured insufficient evidence response without LLM call."""
    provider = MockLLMProvider(simulate_error=True)  # Should NOT be called
    synthesizer = SynthesizerService(provider=provider)

    res = synthesizer.synthesize(SynthesisRequest(
        question="What are the secret rules?",
        passages=[]
    ))

    assert "Insufficient evidence" in res.candidate_answer
    assert res.total_claims == 0
    assert len(res.missing_information) > 0


# ============================================================================
# 9. FastAPI HTTP Endpoint Tests
# ============================================================================

def test_api_synthesize_endpoint_success():
    """POST /api/v1/synthesize successfully returns validated synthesis response."""
    c_id = str(uuid.uuid4())
    payload = {
        "question": "What is the policy threshold?",
        "passages": [
            {
                "chunk_id": c_id,
                "document_id": str(uuid.uuid4()),
                "version_id": str(uuid.uuid4()),
                "chunk_index": 0,
                "text": "The documented threshold is 100 requests per minute.",
                "score": 0.88,
                "source_title": "Rate Limit Standard"
            }
        ]
    }

    # Configure API synthesizer service with mock response
    mock_payload = {
        "candidate_answer": "The threshold is 100 requests per minute.",
        "claims": [
            {
                "claim_id": "c-1",
                "text": "The threshold is 100 requests per minute.",
                "claim_type": "FACT",
                "supporting_chunk_ids": [c_id]
            }
        ],
        "uncertainty_notes": [],
        "missing_information": []
    }
    synthesizer_service.provider = MockLLMProvider(custom_response=json.dumps(mock_payload))

    response = client.post("/api/v1/synthesize", json=payload)
    assert response.status_code == 200

    data = response.json()
    assert data["candidate_answer"] == "The threshold is 100 requests per minute."
    assert data["total_claims"] == 1
    assert data["is_fully_grounded"] is True
    assert data["claims"][0]["valid_chunk_ids"] == [c_id]


def test_api_synthesize_endpoint_timeout_returns_504():
    """POST /api/v1/synthesize returns 504 Gateway Timeout when LLM times out."""
    synthesizer_service.provider = MockLLMProvider(simulate_timeout=True)

    payload = {
        "question": "Question?",
        "passages": [
            {
                "chunk_id": str(uuid.uuid4()),
                "document_id": str(uuid.uuid4()),
                "version_id": str(uuid.uuid4()),
                "chunk_index": 0,
                "text": "Some text",
                "score": 0.5
            }
        ]
    }
    res = client.post("/api/v1/synthesize", json=payload)
    assert res.status_code == 504
    assert "LLMTimeoutError" in str(res.json())


def test_api_synthesize_endpoint_provider_error_returns_502():
    """POST /api/v1/synthesize returns 502 Bad Gateway when LLM provider fails."""
    synthesizer_service.provider = MockLLMProvider(simulate_error=True)

    payload = {
        "question": "Question?",
        "passages": [
            {
                "chunk_id": str(uuid.uuid4()),
                "document_id": str(uuid.uuid4()),
                "version_id": str(uuid.uuid4()),
                "chunk_index": 0,
                "text": "Some text",
                "score": 0.5
            }
        ]
    }
    res = client.post("/api/v1/synthesize", json=payload)
    assert res.status_code == 502
    assert "LLMProviderError" in str(res.json())


def test_api_synthesize_endpoint_malformed_returns_422():
    """POST /api/v1/synthesize returns 422 Unprocessable Content when LLM outputs invalid JSON."""
    synthesizer_service.provider = MockLLMProvider(simulate_malformed=True)

    payload = {
        "question": "Question?",
        "passages": [
            {
                "chunk_id": str(uuid.uuid4()),
                "document_id": str(uuid.uuid4()),
                "version_id": str(uuid.uuid4()),
                "chunk_index": 0,
                "text": "Some text",
                "score": 0.5
            }
        ]
    }
    res = client.post("/api/v1/synthesize", json=payload)
    assert res.status_code == 422
    assert "MalformedLLMResponseError" in str(res.json())


def test_api_synthesize_auto_invokes_retriever_when_passages_omitted():
    """POST /api/v1/synthesize automatically calls Retriever Agent if passages are omitted."""
    doc_id = str(uuid.uuid4())
    ver_id = str(uuid.uuid4())

    # Index sample chunk
    client.post("/api/v1/index-version", json={
        "document_id": doc_id,
        "version_id": ver_id,
        "text": "TruthGuard Synthesizer integration test policy text.",
        "source_title": "Integration Spec"
    })

    # Reset synthesizer provider to mock provider with valid structured response
    sample_response = json.dumps({
        "candidate_answer": "TruthGuard Synthesizer integration test policy text.",
        "claims": [
            {
                "claim_id": "c-1",
                "text": "TruthGuard Synthesizer integration test policy text.",
                "claim_type": "FACT",
                "cited_chunk_ids": [],
                "valid_chunk_ids": [],
                "invalid_chunk_ids": [],
                "is_grounded": True
            }
        ],
        "uncertainty_notes": [],
        "missing_information": []
    })
    synthesizer_service.provider = MockLLMProvider(custom_response=sample_response)

    # Call /synthesize with only question (no passages key)
    res = client.post("/api/v1/synthesize", json={
        "question": "What is the integration test policy text?"
    })
    assert res.status_code == 200
    data = res.json()
    assert "candidate_answer" in data
    assert data["total_claims"] >= 1
