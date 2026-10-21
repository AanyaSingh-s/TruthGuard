"""Unit and API integration tests for RAG text chunking, embedding, and vector storage."""

import math
import uuid
from typing import List
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.schemas.indexing import (
    DocumentChunk,
    IndexingStatus,
    IndexVersionRequest,
    PageContent,
)
from app.services.chunker import TextChunker
from app.services.embedder import EmbeddingError, EmbeddingService
from app.services.indexer import EmptyInputError, IndexingError, IndexingService
from app.db.vector_store import VectorStore

client = TestClient(app)


# ============================================================================
# Chunker Unit Tests
# ============================================================================

def test_chunk_boundaries_and_overlap():
    """Verify text is split into overlapping chunks respecting sentence delimiters."""
    chunker = TextChunker(default_chunk_size=120, default_chunk_overlap=30)
    sample_text = (
        "Section 1: Identity & Access Management. All user credentials must be salted. "
        "Passwords must be at least 16 characters in length. "
        "Multi-factor authentication is mandatory for all administrative access. "
        "Tokens must expire after fifteen minutes of inactivity. "
        "Auditing logs must be retained for seven years in immutable storage."
    )

    chunks = chunker.split_raw_text(sample_text, chunk_size=120, chunk_overlap=30)
    assert len(chunks) >= 2, "Expected multiple chunks for text longer than chunk_size"

    for i in range(len(chunks) - 1):
        curr_text, curr_start, curr_end = chunks[i]
        next_text, next_start, next_end = chunks[i + 1]

        # Verify monotonicity of start positions
        assert next_start > curr_start
        # Verify next chunk starts before current chunk ends (overlap)
        assert next_start < curr_end, f"Expected overlap between chunk {i} and {i+1}"
        assert curr_end - next_start <= 40, "Overlap should remain bounded near target overlap"


def test_chunk_size_and_overlap_validation():
    """Verify validation of chunk size and overlap parameters."""
    chunker = TextChunker()

    with pytest.raises(ValueError, match="chunk_size must be positive"):
        chunker.split_raw_text("Some text", chunk_size=0, chunk_overlap=0)

    with pytest.raises(ValueError, match="chunk_overlap cannot be negative"):
        chunker.split_raw_text("Some text", chunk_size=100, chunk_overlap=-5)

    with pytest.raises(ValueError, match="chunk_overlap .* must be strictly less than chunk_size"):
        chunker.split_raw_text("Some text", chunk_size=100, chunk_overlap=100)


def test_chunk_metadata_preservation():
    """Verify document_id, version_id, chunk_index, source_title, and metadata preservation."""
    chunker = TextChunker(default_chunk_size=100, default_chunk_overlap=25)
    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()
    title = "Engineering Security Standard v2.0"
    custom_metadata = {"department": "InfraSec", "classification": "Confidential"}

    text = (
        "First requirement: zero trust architecture. "
        "Second requirement: cryptographic agility and TLS 1.3 only. "
        "Third requirement: least privilege access controls across all services."
    )

    chunks = chunker.chunk_version(
        document_id=doc_id,
        version_id=ver_id,
        text=text,
        source_title=title,
        metadata=custom_metadata,
        chunk_size=100,
        chunk_overlap=25
    )

    assert len(chunks) > 0
    for idx, c in enumerate(chunks):
        assert c.document_id == doc_id
        assert c.version_id == ver_id
        assert c.chunk_index == idx, "Chunk indices must be sequential 0, 1, 2..."
        assert c.source_title == title
        assert c.metadata["department"] == "InfraSec"
        assert c.metadata["classification"] == "Confidential"
        assert "char_start" in c.metadata
        assert "char_end" in c.metadata
        assert c.metadata["char_count"] == len(c.chunk_text)
        assert c.metadata["word_count"] == len(c.chunk_text.split())


def test_page_number_preservation_with_explicit_pages():
    """Verify page numbers are attached to chunks when explicit page content is provided."""
    chunker = TextChunker(default_chunk_size=80, default_chunk_overlap=20)
    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()

    pages = [
        PageContent(page_number=1, text="Page 1 Content: Overview of the authentication protocols and requirements."),
        PageContent(page_number=2, text="Page 2 Content: Detailed specifications for rate limiting and token revocation.")
    ]

    chunks = chunker.chunk_version(
        document_id=doc_id,
        version_id=ver_id,
        text="dummy",
        pages=pages,
        chunk_size=80,
        chunk_overlap=20
    )

    assert len(chunks) >= 2
    page1_chunks = [c for c in chunks if c.page_number == 1]
    page2_chunks = [c for c in chunks if c.page_number == 2]

    assert len(page1_chunks) > 0
    assert len(page2_chunks) > 0
    assert all("Page 1" in c.chunk_text for c in page1_chunks)
    assert all("Page 2" in c.chunk_text for c in page2_chunks)


def test_page_number_preservation_with_form_feed():
    """Verify form feed characters (\\f) split pages and preserve correct page numbering."""
    chunker = TextChunker(default_chunk_size=100, default_chunk_overlap=20)
    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()

    text_with_form_feed = "This is content from page one.\fThis is content from page two."
    chunks = chunker.chunk_version(
        document_id=doc_id,
        version_id=ver_id,
        text=text_with_form_feed,
        chunk_size=100,
        chunk_overlap=20
    )

    assert len(chunks) == 2
    assert chunks[0].page_number == 1
    assert "page one" in chunks[0].chunk_text
    assert chunks[1].page_number == 2
    assert "page two" in chunks[1].chunk_text


# ============================================================================
# Embedder Unit Tests
# ============================================================================

def test_embedding_dimension_and_l2_normalization():
    """Verify embedding vectors have exact dimension (384) and L2 unit norm (||v|| = 1.0)."""
    embedder = EmbeddingService(dimension=384)
    sample_text = "TruthGuard adversarial claim verification engine."

    vector = embedder.embed_text(sample_text)
    assert len(vector) == 384, "Embedding vector must have strictly 384 dimensions"

    # Verify L2 unit normalization
    norm = math.sqrt(sum(x * x for x in vector))
    assert pytest.approx(norm, rel=1e-5) == 1.0, f"Vector norm {norm} should equal 1.0"


def test_deterministic_embedding_consistency():
    """Verify identical text produces identical embedding vectors."""
    embedder = EmbeddingService(dimension=384)
    text = "Deterministic embedding test string."

    v1 = embedder.embed_text(text)
    v2 = embedder.embed_text(text)
    assert v1 == v2, "Identical inputs must produce identical vector embeddings"


def test_embedder_rejects_empty_input():
    """Verify embedder rejects empty or whitespace-only strings."""
    embedder = EmbeddingService()

    with pytest.raises(EmbeddingError, match="Cannot embed empty text"):
        embedder.embed_text("")

    with pytest.raises(EmbeddingError, match="Cannot embed empty text"):
        embedder.embed_text("   \n\t   ")


def test_embedder_failure_simulation():
    """Verify embedding failure raises EmbeddingError when simulate_failure is enabled."""
    failing_embedder = EmbeddingService(simulate_failure=True)

    with pytest.raises(EmbeddingError, match="Simulated embedding inference failure"):
        failing_embedder.embed_text("Test content")

    with pytest.raises(EmbeddingError, match="Simulated batch embedding failure"):
        failing_embedder.embed_batch(["Test content 1", "Test content 2"])


# ============================================================================
# Indexing Service & Idempotency Tests
# ============================================================================

def test_indexing_rejects_empty_and_whitespace_input():
    """Verify IndexingService rejects empty and whitespace-only text inputs."""
    service = IndexingService(store=VectorStore(force_in_memory=True))

    req_empty = IndexVersionRequest(
        document_id=uuid.uuid4(),
        version_id=uuid.uuid4(),
        text=""
    )
    with pytest.raises(EmptyInputError, match="contains no text to index"):
        service.index_version(req_empty)

    req_whitespace = IndexVersionRequest(
        document_id=uuid.uuid4(),
        version_id=uuid.uuid4(),
        text="   \n\t  \n  "
    )
    with pytest.raises(EmptyInputError, match="contains no text to index"):
        service.index_version(req_whitespace)


def test_idempotent_repeated_indexing_same_content():
    """Verify reprocessing a version replaces previous chunks without duplicates."""
    store = VectorStore(force_in_memory=True)
    service = IndexingService(store=store)

    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()
    text = (
        "Document Paragraph 1: System requirements and compliance standards. "
        "Document Paragraph 2: Access control lists and role-based permissions. "
        "Document Paragraph 3: Audit logging and retention periods."
    )

    req = IndexVersionRequest(
        document_id=doc_id,
        version_id=ver_id,
        text=text,
        source_title="Idempotency Test Doc",
        chunk_size=100,
        chunk_overlap=20
    )

    # First indexing pass
    res1 = service.index_version(req)
    assert res1.status == IndexingStatus.INDEXED
    initial_count = res1.chunk_count
    assert initial_count > 1

    chunks1 = service.get_version_chunks(ver_id)
    assert len(chunks1) == initial_count

    # Second indexing pass with exact same request
    res2 = service.index_version(req)
    assert res2.status == IndexingStatus.INDEXED
    assert res2.chunk_count == initial_count

    chunks2 = service.get_version_chunks(ver_id)
    assert len(chunks2) == initial_count, "Repeated indexing must not double or duplicate chunks"

    # Indices must remain strictly 0 .. initial_count - 1
    indices = [c.chunk_index for c in chunks2]
    assert indices == list(range(initial_count))


def test_idempotent_reindexing_with_updated_content():
    """Verify reindexing with different content replaces old chunks with new ones."""
    store = VectorStore(force_in_memory=True)
    service = IndexingService(store=store)

    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()

    long_text = "Word " * 200  # generates multiple chunks
    req_long = IndexVersionRequest(
        document_id=doc_id,
        version_id=ver_id,
        text=long_text,
        chunk_size=100,
        chunk_overlap=20
    )
    res_long = service.index_version(req_long)
    count_long = res_long.chunk_count
    assert count_long > 3

    # Now re-index same version with very short text (1 chunk)
    short_text = "Short replacement text."
    req_short = IndexVersionRequest(
        document_id=doc_id,
        version_id=ver_id,
        text=short_text,
        chunk_size=100,
        chunk_overlap=20
    )
    res_short = service.index_version(req_short)
    assert res_short.chunk_count == 1

    current_chunks = service.get_version_chunks(ver_id)
    assert len(current_chunks) == 1
    assert current_chunks[0].chunk_text == "Short replacement text."


def test_cross_version_isolation():
    """Verify indexing one version does not overwrite or remove chunks of another version."""
    store = VectorStore(force_in_memory=True)
    service = IndexingService(store=store)

    doc_id = uuid.uuid4()
    ver1_id = uuid.uuid4()
    ver2_id = uuid.uuid4()

    service.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=ver1_id,
        text="Version 1 content for document isolation test.",
        chunk_size=150,
        chunk_overlap=30
    ))
    service.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=ver2_id,
        text="Version 2 content for document isolation test.",
        chunk_size=150,
        chunk_overlap=30
    ))

    # Re-index version 1
    service.index_version(IndexVersionRequest(
        document_id=doc_id,
        version_id=ver1_id,
        text="Version 1 updated content.",
        chunk_size=150,
        chunk_overlap=30
    ))

    # Verify version 2 is untouched
    ver2_chunks = service.get_version_chunks(ver2_id)
    assert len(ver2_chunks) == 1
    assert "Version 2" in ver2_chunks[0].chunk_text


def test_embedding_failure_records_failed_status_and_cleans_chunks():
    """Verify embedding failure records status as FAILED and leaves no dangling active chunks."""
    store = VectorStore(force_in_memory=True)
    failing_embedder = EmbeddingService(simulate_failure=True)
    service = IndexingService(embedder=failing_embedder, store=store)

    doc_id = uuid.uuid4()
    ver_id = uuid.uuid4()

    req = IndexVersionRequest(
        document_id=doc_id,
        version_id=ver_id,
        text="Valid text that will fail during embedding phase."
    )

    with pytest.raises(IndexingError, match="Embedding failed"):
        service.index_version(req)

    # Check recorded status
    status_info = service.get_status(ver_id)
    assert status_info is not None
    assert status_info["status"] == IndexingStatus.FAILED.value
    assert "Simulated batch embedding failure" in status_info["error_message"]

    # Verify no corrupt chunks are left active
    chunks = service.get_version_chunks(ver_id)
    assert len(chunks) == 0


# ============================================================================
# FastAPI HTTP Endpoint Tests
# ============================================================================

def test_api_index_version_success():
    """POST /api/v1/index-version successfully indexes document version."""
    doc_id = str(uuid.uuid4())
    ver_id = str(uuid.uuid4())
    payload = {
        "document_id": doc_id,
        "version_id": ver_id,
        "text": "Enterprise cloud security baseline. Chunks are embedded and stored in pgvector.",
        "source_title": "Security Baseline",
        "metadata": {"confidentiality": "restricted"},
        "chunk_size": 150,
        "chunk_overlap": 30
    }

    response = client.post("/api/v1/index-version", json=payload)
    assert response.status_code == 200

    data = response.json()
    assert data["document_id"] == doc_id
    assert data["version_id"] == ver_id
    assert data["status"] == "INDEXED"
    assert data["chunk_count"] >= 1
    assert data["embedding_model"] == "sentence-transformers/all-MiniLM-L6-v2"
    assert data["embedding_dimension"] == 384
    assert data["error_message"] is None


def test_api_index_version_rejects_empty_content():
    """POST /api/v1/index-version rejects empty text with 400 Bad Request."""
    payload = {
        "document_id": str(uuid.uuid4()),
        "version_id": str(uuid.uuid4()),
        "text": "   "
    }

    response = client.post("/api/v1/index-version", json=payload)
    assert response.status_code == 400
    data = response.json()
    assert "EmptyInputError" in str(data)


def test_api_inspect_chunks_and_embeddings():
    """GET /api/v1/chunks/{version_id} retrieves chunks and vector embeddings."""
    doc_id = str(uuid.uuid4())
    ver_id = str(uuid.uuid4())
    payload = {
        "document_id": doc_id,
        "version_id": ver_id,
        "text": "Inspectable chunk content for TruthGuard Day 4 verification.",
        "source_title": "Inspection Spec"
    }
    client.post("/api/v1/index-version", json=payload)

    # 1. Fetch chunks with embeddings
    res_with_emb = client.get(f"/api/v1/chunks/{ver_id}?include_embeddings=true")
    assert res_with_emb.status_code == 200
    data = res_with_emb.json()
    assert data["version_id"] == ver_id
    assert data["total_chunks"] >= 1
    chunk = data["chunks"][0]
    assert chunk["chunk_index"] == 0
    assert chunk["source_title"] == "Inspection Spec"
    assert len(chunk["embedding"]) == 384

    # 2. Fetch chunks without embeddings
    res_no_emb = client.get(f"/api/v1/chunks/{ver_id}?include_embeddings=false")
    assert res_no_emb.status_code == 200
    data_no_emb = res_no_emb.json()
    assert data_no_emb["chunks"][0]["embedding"] is None


def test_api_get_indexing_status():
    """GET /api/v1/indexing-status/{version_id} returns accurate status."""
    ver_id = str(uuid.uuid4())

    # Check non-existent version returns 404
    res_not_indexed = client.get(f"/api/v1/indexing-status/{ver_id}")
    assert res_not_indexed.status_code == 404

    # Index document
    client.post("/api/v1/index-version", json={
        "document_id": str(uuid.uuid4()),
        "version_id": ver_id,
        "text": "Indexed status check content."
    })

    # Check indexed version
    res_indexed = client.get(f"/api/v1/indexing-status/{ver_id}")
    assert res_indexed.status_code == 200
    data = res_indexed.json()
    assert data["status"] == "INDEXED"
    assert data["chunk_count"] >= 1
    assert data["embedding_dimension"] == 384
    assert data["embedding_model"] == "sentence-transformers/all-MiniLM-L6-v2"
