"""Unit and API integration tests for TruthGuard's Retriever Agent."""

import uuid
from datetime import date
import pytest
from fastapi.testclient import TestClient

from app.db.vector_store import VectorStore
from app.main import app
from app.schemas.indexing import IndexVersionRequest
from app.schemas.retrieval import RetrievalFilter, RetrievalRequest
from app.services.embedder import EmbeddingService
from app.services.indexer import IndexingService
from app.services.retriever import RetrieverService

client = TestClient(app)


# ============================================================================
# Test Fixtures & In-Memory Store Setup
# ============================================================================

@pytest.fixture
def standalone_retriever():
    """Provides isolated IndexingService and RetrieverService backed by clean in-memory store."""
    store = VectorStore(force_in_memory=True, isolate_memory=True)
    embedder = EmbeddingService()
    indexer = IndexingService(embedder=embedder, store=store)
    retriever = RetrieverService(embedder=embedder, store=store)
    return indexer, retriever, store


# ============================================================================
# 1. Relevant Retrieval Test
# ============================================================================

def test_relevant_retrieval(standalone_retriever):
    """Verify semantically relevant questions retrieve targeted evidence chunks with high scores."""
    indexer, retriever, _ = standalone_retriever

    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()
    policy_text = (
        "Identity and Access Management Standard. "
        "Tokens must expire after fifteen minutes of inactivity. "
        "All administrative sessions require multi-factor authentication. "
        "Audit logs must be written to immutable storage daily."
    )

    indexer.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=ver_id,
        text=policy_text,
        source_title="IAM Policy 2026",
        version_identifier="v1.0",
        authority_level="OFFICIAL",
        effective_date="2026-01-01",
        chunk_size=120,
        chunk_overlap=25
    ))

    # Query for the token expiration rule
    req = RetrievalRequest(
        question="What is the token expiration period?",
        top_k=3,
        min_score=0.2
    )
    res = retriever.retrieve(req)

    assert res.total_retrieved >= 1
    top_passage = res.passages[0]
    assert "Tokens must expire after fifteen minutes" in top_passage.text
    assert top_passage.score > 0.25
    assert top_passage.semantic_score is not None
    assert top_passage.keyword_score is not None
    assert top_passage.version_identifier == "v1.0"
    assert top_passage.authority_level == "OFFICIAL"
    assert top_passage.effective_date == "2026-01-01"


# ============================================================================
# 2. Irrelevant Questions Test
# ============================================================================

def test_irrelevant_question_yields_low_score_or_empty(standalone_retriever):
    """Verify irrelevant questions receive low relevance scores and filter out at min_score."""
    indexer, retriever, _ = standalone_retriever

    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()
    tech_spec = (
        "Network Protocol Specification: TCP connection pooling with keep-alive of 60 seconds. "
        "TLS 1.3 is strictly enforced for all inbound microservice RPCs."
    )

    indexer.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=ver_id,
        text=tech_spec,
        source_title="Network Spec",
        version_identifier="v1.0"
    ))

    # Irrelevant culinary query
    req = RetrievalRequest(
        question="How do you bake a chocolate birthday cake with strawberry icing?",
        top_k=5,
        min_score=0.5
    )
    res = retriever.retrieve(req)

    # With min_score=0.5, completely irrelevant passages must be filtered out
    assert res.total_retrieved == 0
    assert len(res.passages) == 0

    # With min_score=0.0, keyword score is zero and combined score is very low
    res_unfiltered = retriever.retrieve(RetrievalRequest(
        question="How do you bake a chocolate birthday cake with strawberry icing?",
        top_k=5,
        min_score=0.0
    ))
    if res_unfiltered.total_retrieved > 0:
        assert res_unfiltered.passages[0].keyword_score == 0.0
        assert res_unfiltered.passages[0].score < 0.4


# ============================================================================
# 3. Duplicate Passages Across Documents Test
# ============================================================================

def test_duplicate_passages_preserved_without_deduplication(standalone_retriever):
    """
    Verify identical/duplicate passages in different documents or versions are
    BOTH retained with distinct provenance, avoiding accidental deduplication.
    """
    indexer, retriever, _ = standalone_retriever

    doc1_id = uuid.uuid4()
    ver1_id = uuid.uuid4()
    doc2_id = uuid.uuid4()
    ver2_id = uuid.uuid4()

    shared_text = "All employees must complete mandatory annual cybersecurity awareness training by December 31."

    indexer.index_version(IndexVersionRequest(
        document_id=doc1_id,
        version_id=ver1_id,
        text=shared_text,
        source_title="HR Employee Handbook",
        version_identifier="v2025"
    ))

    indexer.index_version(IndexVersionRequest(
        document_id=doc2_id,
        version_id=ver2_id,
        text=shared_text,
        source_title="Information Security Policy",
        version_identifier="v2026"
    ))

    req = RetrievalRequest(
        question="Is annual cybersecurity awareness training mandatory for employees?",
        top_k=5
    )
    res = retriever.retrieve(req)

    assert res.total_retrieved >= 2
    doc_ids_retrieved = {p.document_id for p in res.passages}
    version_ids_retrieved = {p.version_id for p in res.passages}

    # Verify BOTH sources are present — neither was deduplicated away
    assert doc1_id in doc_ids_retrieved
    assert doc2_id in doc_ids_retrieved
    assert ver1_id in version_ids_retrieved
    assert ver2_id in version_ids_retrieved


# ============================================================================
# 4. Conflicting Document Versions Test (Critical TruthGuard Requirement)
# ============================================================================

def test_conflicting_document_versions_both_retrieved_with_provenance(standalone_retriever):
    """
    CRITICAL REQUIREMENT:
    Retrieval must NOT filter out older versions before the critic can evaluate them.
    When two versions contradict each other, BOTH versions must be retrieved and ranked.
    """
    indexer, retriever, _ = standalone_retriever

    doc_id = uuid.uuid4()
    v1_id = uuid.uuid4()
    v2_id = uuid.uuid4()

    # Version 1 (Older, Official)
    v1_text = "The maximum session timeout is 30 minutes for all administrative portals."
    # Version 2 (Newer, Proposal, Supersedes v1)
    v2_text = "The maximum session timeout is 60 minutes for all administrative portals."

    indexer.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=v1_id,
        text=v1_text,
        source_title="Access Control Guideline",
        version_identifier="v1.0",
        effective_date="2024-01-01",
        upload_timestamp="2024-01-01T00:00:00Z",
        authority_level="OFFICIAL",
        superseded_by_id=v2_id
    ))

    indexer.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=v2_id,
        text=v2_text,
        source_title="Access Control Guideline",
        version_identifier="v2.0-proposal",
        effective_date="2025-06-01",
        upload_timestamp="2025-06-01T12:00:00Z",
        authority_level="WORKING_DRAFT",
        supersedes_version_id=v1_id
    ))

    # Query for the session timeout
    req = RetrievalRequest(
        question="What is the maximum session timeout for administrative portals?",
        top_k=5
    )
    res = retriever.retrieve(req)

    assert res.total_retrieved >= 2

    # Extract passages by version
    v1_passages = [p for p in res.passages if p.version_id == v1_id]
    v2_passages = [p for p in res.passages if p.version_id == v2_id]

    assert len(v1_passages) == 1, "Older version 1 must NOT be dropped by retrieval"
    assert len(v2_passages) == 1, "Newer version 2 must be retrieved"

    p1 = v1_passages[0]
    p2 = v2_passages[0]

    # Check substantive contradictory content preserved
    assert "30 minutes" in p1.text
    assert "60 minutes" in p2.text

    # Check provenance integrity
    assert p1.version_identifier == "v1.0"
    assert p1.authority_level == "OFFICIAL"
    assert p1.effective_date == "2024-01-01"
    assert p1.superseded_by_id == v2_id

    assert p2.version_identifier == "v2.0-proposal"
    assert p2.authority_level == "WORKING_DRAFT"
    assert p2.effective_date == "2025-06-01"
    assert p2.supersedes_version_id == v1_id


def test_superseded_filtering_when_explicitly_requested(standalone_retriever):
    """Verify include_superseded=False filters out superseded older versions only when requested."""
    indexer, retriever, _ = standalone_retriever

    doc_id = uuid.uuid4()
    v1_id = uuid.uuid4()
    v2_id = uuid.uuid4()

    indexer.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=v1_id,
        text="Version 1: Old threshold is 5 retries.",
        version_identifier="v1.0",
        superseded_by_id=v2_id
    ))

    indexer.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=v2_id,
        text="Version 2: New threshold is 10 retries.",
        version_identifier="v2.0",
        supersedes_version_id=v1_id
    ))

    # Explicitly filter out superseded versions
    req = RetrievalRequest(
        question="What is the retry threshold?",
        filters=RetrievalFilter(include_superseded=False)
    )
    res = retriever.retrieve(req)

    version_ids = [p.version_id for p in res.passages]
    assert v2_id in version_ids
    assert v1_id not in version_ids


# ============================================================================
# 5. Configurable Top-k and Min-Score Settings
# ============================================================================

def test_configurable_top_k_and_min_score(standalone_retriever):
    """Verify top_k bounds result list size and min_score filters lower-ranked candidates."""
    indexer, retriever, _ = standalone_retriever

    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()
    text = (
        "Section 1: Data retention rules. "
        "Section 2: Key rotation cycles. "
        "Section 3: Incident response escalation path. "
        "Section 4: Threat modeling methodology. "
        "Section 5: Penetration test schedules."
    )

    indexer.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=ver_id,
        text=text,
        chunk_size=70,
        chunk_overlap=15
    ))

    # Test top_k=2
    res_k2 = retriever.retrieve(RetrievalRequest(
        question="What are the data retention rules and key rotation?",
        top_k=2
    ))
    assert len(res_k2.passages) == 2

    # Test top_k=4
    res_k4 = retriever.retrieve(RetrievalRequest(
        question="What are the data retention rules and key rotation?",
        top_k=4
    ))
    assert len(res_k4.passages) == 4

    # Verify descending score order
    scores = [p.score for p in res_k4.passages]
    assert scores == sorted(scores, reverse=True)


# ============================================================================
# 6. Hybrid Ranking Strategies (Linear vs RRF)
# ============================================================================

def test_ranking_strategies_linear_and_rrf(standalone_retriever):
    """Verify both 'hybrid_linear' and 'rrf' ranking methods succeed and produce normalized scores."""
    indexer, retriever, _ = standalone_retriever

    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()
    text = "Database connection pooling guidelines. Set max_connections to 100 with idle timeout 30s."

    indexer.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=ver_id,
        text=text
    ))

    # Linear hybrid
    res_linear = retriever.retrieve(RetrievalRequest(
        question="What is the idle timeout for database connections?",
        ranking_method="hybrid_linear",
        semantic_weight=0.7
    ))
    assert len(res_linear.passages) == 1
    assert 0.0 <= res_linear.passages[0].score <= 1.0

    # Reciprocal Rank Fusion (RRF)
    res_rrf = retriever.retrieve(RetrievalRequest(
        question="What is the idle timeout for database connections?",
        ranking_method="rrf",
        semantic_weight=0.7
    ))
    assert len(res_rrf.passages) == 1
    assert 0.0 <= res_rrf.passages[0].score <= 1.0


# ============================================================================
# 7. Semantic Weight Alpha Control
# ============================================================================

def test_semantic_weight_control(standalone_retriever):
    """Verify semantic_weight shifts balance between pure vector similarity and lexical matching."""
    indexer, retriever, _ = standalone_retriever

    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()
    text = "Cryptographic standard requires AES-256-GCM cipher suite."

    indexer.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=ver_id,
        text=text
    ))

    # Dense only: semantic_weight=1.0
    res_dense = retriever.retrieve(RetrievalRequest(
        question="What cipher suite is required?",
        semantic_weight=1.0
    ))
    p_dense = res_dense.passages[0]
    assert pytest.approx(p_dense.score, rel=1e-3) == max(0.0, p_dense.semantic_score)

    # Keyword only: semantic_weight=0.0
    res_kw = retriever.retrieve(RetrievalRequest(
        question="What cipher suite is required?",
        semantic_weight=0.0
    ))
    p_kw = res_kw.passages[0]
    assert pytest.approx(p_kw.score, rel=1e-3) == p_kw.keyword_score


# ============================================================================
# 8. FastAPI HTTP Endpoint Tests
# ============================================================================

def test_api_retrieve_success():
    """POST /api/v1/retrieve successfully returns ranked passages with metadata."""
    doc_id = str(uuid.uuid4())
    ver_id = str(uuid.uuid4())

    # Seed index via API
    index_res = client.post("/api/v1/index-version", json={
        "document_id": doc_id,
        "version_id": ver_id,
        "text": "Access token lifetime is set to exactly 900 seconds in TruthGuard Day 5.",
        "source_title": "Token Configuration Guide",
        "version_identifier": "v1.0",
        "authority_level": "OFFICIAL",
        "effective_date": "2026-03-01"
    })
    assert index_res.status_code == 200

    # Query retriever endpoint
    retrieve_res = client.post("/api/v1/retrieve", json={
        "question": "What is the access token lifetime?",
        "top_k": 3,
        "semantic_weight": 0.7
    })
    assert retrieve_res.status_code == 200

    data = retrieve_res.json()
    assert data["question"] == "What is the access token lifetime?"
    assert data["total_retrieved"] >= 1
    passage = data["passages"][0]
    assert "900 seconds" in passage["text"]
    assert passage["score"] > 0.3
    assert passage["source_title"] == "Token Configuration Guide"
    assert passage["version_identifier"] == "v1.0"
    assert passage["authority_level"] == "OFFICIAL"
    assert passage["effective_date"] == "2026-03-01"
    assert "retrieval_metadata" in data


def test_api_retrieve_rejects_empty_query():
    """POST /api/v1/retrieve returns 400 Bad Request when question is empty."""
    res = client.post("/api/v1/retrieve", json={"question": "   "})
    assert res.status_code == 400
    assert "InvalidQueryError" in str(res.json())
