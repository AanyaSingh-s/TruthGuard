# TruthGuard AI Service

The Python FastAPI AI Service handles vector embeddings, semantic retrieval, adversarial inspection, and candidate answer generation.

## Responsibilities
- Embedding computation and semantic vector search in PostgreSQL (pgvector).
- Candidate answer generation grounded strictly in retrieved context.
- Adversarial cross-examination of candidate answers for unsupported claims, temporal drift/staleness, and logical contradictions.
- Fact/claim extraction for deterministic rule arbitration.

## Technology Stack
- **Python**: 3.10+ (Tested on Python 3.14)
- **Framework**: FastAPI
- **Server**: Uvicorn
- **Validation**: Pydantic v2
- **Testing**: pytest, httpx

## Endpoints

| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/health` | Service health status and subsystem capability check |
| `GET` | `/api/v1/health` | Versioned alias for health check |
| `POST` | `/api/v1/extract-text` | Multipart document text extraction (TXT, PDF, DOCX) |
| `POST` | `/api/v1/index-version` | Split version text into overlapping chunks, compute embeddings, and store in pgvector |
| `GET` | `/api/v1/chunks/{version_id}` | Inspect chunks, metadata, and dense embedding vectors |
| `GET` | `/api/v1/indexing-status/{version_id}` | Retrieve indexing status, chunk counts, model info, or failure error |
| `POST` | `/api/v1/retrieve` | Retrieve ranked evidence passages with hybrid scoring and provenance |
| `POST` | `/api/v1/synthesize` | Synthesize grounded candidate answer with atomic claims and verified evidence citations |
| `GET` | `/docs` | OpenAPI / Swagger interactive documentation |
| `GET` | `/redoc` | ReDoc API specification |

---

## 🧠 RAG Indexing & Embedding Pipeline (Day 4)

TruthGuard's vector indexing pipeline chunks processed document versions, generates dense vector representations, and persists chunks and embeddings in PostgreSQL with pgvector.

### 1. Documented Embedding Model

| Attribute | Specification |
| :--- | :--- |
| **Model Name** | `sentence-transformers/all-MiniLM-L6-v2` |
| **Vector Dimension** | **384** |
| **Normalization** | Strict $L_2$ unit normalization ($\|\mathbf{v}\|_2 = 1.0$) |
| **Similarity Metric** | Cosine Similarity / Inner Product ($\cos(\theta) = \mathbf{u} \cdot \mathbf{v}$) |
| **Offline Fallback** | Deterministic 384-d $L_2$-normalized projection engine for air-gapped / sandbox runs |

> [!IMPORTANT]
> **Semantic Similarity vs. Token Estimation**:
> Dense embeddings are used exclusively for **semantic similarity retrieval** and passage relevance ranking. Embeddings are **never** used to estimate the number of tokens an answer will need.

---

### 2. Chunking & Provenance Preservation

Text chunking is managed by [`TextChunker`](file:///Users/arjunsingh/Downloads/TruthGuard/ai-service/app/services/chunker.py):
- **Configurable Sliding Window**: Defaults to 500 characters with 100 character overlap (`chunk_size` and `chunk_overlap` customizable per request).
- **Boundary-Aware Splitting**: Searches backwards from chunk window end for sentence and paragraph boundaries (`. `, `\n\n`, `\n`, ` `) to avoid cutting words or assertions in half.
- **Page Number Tracking**: Preserves explicit page boundaries (from PDF multi-page extraction) or form feed `\f` separators.
- **Provenance Retention**: Each chunk attaches:
  - `document_id` and `version_id` (foreign keys linking back to the document catalog).
  - `chunk_index` (strictly sequential $0, 1, 2, \dots$).
  - `source_title` and `page_number`.
  - `metadata` dictionary containing character offsets (`char_start`, `char_end`), `char_count`, `word_count`, and custom classification tags.

---

### 3. Idempotent Storage & Failure Handling

Vector storage is managed by [`VectorStore`](file:///Users/arjunsingh/Downloads/TruthGuard/ai-service/app/db/vector_store.py) and [`IndexingService`](file:///Users/arjunsingh/Downloads/TruthGuard/ai-service/app/services/indexer.py):
- **Idempotency**: Reprocessing a document version runs inside a single database transaction:
  ```sql
  DELETE FROM document_chunks WHERE version_id = %s;
  INSERT INTO document_chunks (...) VALUES (...);
  ```
  This guarantees that repeated indexing replaces obsolete chunks without generating duplicate active chunks or leaving dangling entries.
- **Status Tracking**: The `indexing_status` table records lifecycle state:
  - `INDEXING`: Processing in progress.
  - `INDEXED`: Successfully chunked, embedded, and persisted.
  - `FAILED`: Failure recorded with detailed `error_message`, and any partial chunks are automatically rolled back.

---

### 4. How to Change Embedding Models Safely

Switching embedding models (e.g. from `all-MiniLM-L6-v2` (384-d) to `bge-base-en-v1.5` (768-d) or `text-embedding-3-small` (1536-d)) requires a disciplined migration because vector spaces across different models are strictly incompatible and vector dimensions may differ.

#### Migration Runbook:

1. **Update AI Service Configuration**:
   Update `ai-service/app/config.py` (or set environment variables):
   ```bash
   EMBEDDING_MODEL_NAME="BAAI/bge-base-en-v1.5"
   EMBEDDING_DIMENSION=768
   ```

2. **Alter PostgreSQL Schema**:
   If the new model has a different embedding dimension (e.g. 768 vs 384):
   ```sql
   -- Option A: Direct Alteration (with maintenance window)
   ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(768);
   DROP INDEX IF EXISTS idx_chunks_embedding_cosine;
   CREATE INDEX idx_chunks_embedding_cosine ON document_chunks USING hnsw (embedding vector_cosine_ops);

   -- Option B: Zero-Downtime Blue/Green Column (recommended for production)
   ALTER TABLE document_chunks ADD COLUMN embedding_v2 vector(768);
   CREATE INDEX idx_chunks_embedding_v2_cosine ON document_chunks USING hnsw (embedding_v2 vector_cosine_ops);
   ```

3. **Reindex Existing Document Versions**:
   Because embeddings from different models cannot be compared via cosine similarity, all existing active document versions must be re-embedded:
   - Fetch all active document versions from `document_versions`.
   - Call `POST /api/v1/index-version` for each version.
   - The idempotent pipeline atomically deletes old chunks and inserts new chunks with the new dimension and model name.

4. **Verify Migration**:
   - Inspect chunks via `GET /api/v1/chunks/{version_id}?include_embeddings=true` and confirm the `embedding_dimension` equals 768.
   - Check `GET /api/v1/indexing-status/{version_id}` and verify `embedding_model` reflects the new model.
   - Execute similarity retrieval smoke tests to confirm expected rankings.

### Example Health Response
```json
{
  "status": "UP",
  "service": "truthguard-ai-service",
  "version": "0.1.0",
  "environment": "development",
  "timestamp": "2026-10-20T14:00:00Z",
  "details": {
    "role": "Adversarial verification, claim extraction, and embedding generation",
    "models_ready": "mock_mode_active",
    "vector_search_ready": "configured"
  }
}
```

---

## 🔍 TruthGuard Retriever Agent (Day 5)

The Retriever Agent ([`RetrieverService`](file:///Users/arjunsingh/Downloads/TruthGuard/ai-service/app/services/retriever.py)) implements typed, provenance-preserving evidence retrieval combining dense semantic similarity and lexical keyword matching.

### Core Architecture & Capabilities

1. **Question Embedding**:
   - Encodes input questions using the exact same compatible embedding model used for document chunks: `sentence-transformers/all-MiniLM-L6-v2` (384 dimensions, $L_2$-normalized).
2. **Dense Vector Search**:
   - Queries PostgreSQL with pgvector using cosine distance (`1 - (dc.embedding <=> query_vec)`), or exact dot product in in-memory fallback.
3. **Lexical Keyword Search**:
   - In PostgreSQL: full-text search using `to_tsvector('english', chunk_text) @@ plainto_tsquery('english', query)` ranked with `ts_rank_cd`.
   - In Python/in-memory: rule-normalized stemming and token density scoring via [`LexicalScorer`](file:///Users/arjunsingh/Downloads/TruthGuard/ai-service/app/services/lexical.py).
4. **Documented Hybrid Ranking Strategies**:
   - **`hybrid_linear` (Default)**:
     Convex combination of normalized dense similarity and keyword scores:
     $$\text{Score} = \alpha \cdot \max(0.0, S_{\text{semantic}}) + (1 - \alpha) \cdot S_{\text{keyword}}$$
     where $\alpha$ is `semantic_weight` (default `0.7`).
   - **`rrf` (Reciprocal Rank Fusion)**:
     Rank-based fusion scale-normalized to $[0, 1]$:
     $$\text{RRF}(d) = \frac{\alpha}{60 + \text{rank}_{\text{semantic}}(d)} + \frac{1 - \alpha}{60 + \text{rank}_{\text{keyword}}(d)}$$
5. **Conflict & Contradiction Preservation (TruthGuard Mandate)**:
   > [!IMPORTANT]
   > Retrieval **never** deduplicates away conflicting document versions or discards older versions before the critic can evaluate supersession.
   > If Version 1 specifies *"Timeout is 30 minutes"* and Version 2 specifies *"Timeout is 60 minutes"*, **both passages are returned** with full provenance (`version_identifier`, `authority_level`, `effective_date`, `supersedes_version_id`, `superseded_by_id`).
6. **Configurable Thresholds & Filtering**:
   - `top_k`: Maximum passages returned (default `5`).
   - `min_score`: Minimum relevance threshold below which irrelevant noise is excluded.
   - `filters`: Optional filtering by `document_ids`, `version_ids`, `source_types`, `authority_levels`, and date ranges (`effective_date_from`, `effective_date_to`).
   - `include_superseded`: Defaults to `True` to retain older conflicting versions for inspection.

### Example Retrieval Request
```json
POST /api/v1/retrieve
{
  "question": "What is the token expiration period?",
  "top_k": 3,
  "min_score": 0.25,
  "semantic_weight": 0.7,
  "ranking_method": "hybrid_linear"
}
```

### Example Retrieval Response
```json
{
  "question": "What is the token expiration period?",
  "total_retrieved": 1,
  "passages": [
    {
      "chunk_id": "0399f51e-106a-4df4-a44b-b211b8c2c82e",
      "document_id": "d597f70f-4930-4803-adec-065c1a4851a8",
      "version_id": "d56f9039-9cb9-4de3-b990-de406633d30e",
      "version_identifier": "v1.0",
      "chunk_index": 0,
      "text": "Identity and Access Management Standard. Tokens must expire after fifteen minutes of inactivity.",
      "score": 0.3009,
      "semantic_score": 0.2334,
      "keyword_score": 0.4583,
      "source_title": "IAM Policy 2026",
      "source_type": "POLICY",
      "page_number": 1,
      "effective_date": "2026-01-01",
      "upload_timestamp": "2026-01-01T00:00:00Z",
      "authority_level": "OFFICIAL",
      "supersedes_version_id": null,
      "superseded_by_id": null,
      "metadata": {
        "char_start": 0,
        "char_end": 97
      }
    }
  ],
  "retrieval_metadata": {
    "embedding_model": "sentence-transformers/all-MiniLM-L6-v2",
    "embedding_dimension": 384,
    "ranking_method": "hybrid_linear",
    "semantic_weight": 0.7,
    "top_k": 3,
    "min_score": 0.25,
    "candidates_scanned": 3,
    "include_superseded": true
  }
}
```

---

---

## ✍️ Answer Synthesizer Agent (Day 6)

The Synthesizer Agent ([`SynthesizerService`](file:///Users/arjunsingh/Downloads/TruthGuard/ai-service/app/services/synthesizer.py)) produces evidence-grounded candidate answers decomposed into atomic claims with verified citations.

### Core Principles & Architecture

1. **Strict Evidence Grounding**:
   - The model is instructed via system prompt to answer strictly and exclusively from supplied evidence passages.
   - It separates facts, uncertainties, and missing information rather than fabricating certainty.
2. **Provider Abstraction**:
   - Decoupled from specific LLM vendors via [`LLMProvider`](file:///Users/arjunsingh/Downloads/TruthGuard/ai-service/app/services/llm.py) base class.
   - Implements [`MockLLMProvider`](file:///Users/arjunsingh/Downloads/TruthGuard/ai-service/app/services/llm.py#L58-L151) for offline/sandbox testing and [`OpenAICompatibleProvider`](file:///Users/arjunsingh/Downloads/TruthGuard/ai-service/app/services/llm.py#L153-L200) for production LLMs (OpenAI, vLLM, Ollama, LiteLLM).
3. **Rigorous Citation Verification**:
   > [!WARNING]
   > **No Hallucinated Citations Accepted**:
   > Synthesizer **never** accepts citations merely because the model produced plausible-looking UUIDs. Every cited chunk ID is verified against the actual supplied retrieval context. Hallucinated or non-existent chunk IDs are caught during validation, recorded in `invalid_chunk_ids`, and the claim is flagged as ungrounded (`is_grounded = False`).
4. **Separation of Concerns**:
   > [!IMPORTANT]
   > The Synthesizer does **not** make the final trust decision or arbitrate contradictions. It surfaces the candidate claims and observations so the Critic and Resolution Engine (Days 7–8) can evaluate inconsistencies and precedence.

### Example Synthesis Request
```json
POST /api/v1/synthesize
{
  "question": "What is the token expiration period?",
  "passages": [
    {
      "chunk_id": "0399f51e-106a-4df4-a44b-b211b8c2c82e",
      "document_id": "d597f70f-4930-4803-adec-065c1a4851a8",
      "version_id": "d56f9039-9cb9-4de3-b990-de406633d30e",
      "chunk_index": 0,
      "text": "Identity and Access Management Standard. Tokens must expire after fifteen minutes of inactivity.",
      "score": 0.95,
      "source_title": "IAM Policy 2026"
    }
  ]
}
```

### Example Synthesis Response
```json
{
  "question": "What is the token expiration period?",
  "candidate_answer": "Tokens must expire after fifteen minutes of inactivity according to IAM policy.",
  "claims": [
    {
      "claim_id": "c-1",
      "text": "Tokens must expire after fifteen minutes of inactivity.",
      "claim_type": "FACT",
      "cited_chunk_ids": ["0399f51e-106a-4df4-a44b-b211b8c2c82e"],
      "valid_chunk_ids": ["0399f51e-106a-4df4-a44b-b211b8c2c82e"],
      "invalid_chunk_ids": [],
      "is_grounded": true,
      "unsupported_reason": null
    }
  ],
  "total_claims": 1,
  "supported_claims_count": 1,
  "unsupported_claims_count": 0,
  "is_fully_grounded": true,
  "uncertainty_notes": [],
  "missing_information": [],
  "evidence_chunk_ids": ["0399f51e-106a-4df4-a44b-b211b8c2c82e"],
  "metadata": {
    "provider": "mock",
    "model": "gpt-4o-mini",
    "passages_supplied": 1,
    "unsupported_claims_flagged": false
  }
}
```

---

## Running Locally

### 1. Install Dependencies
```bash
cd ai-service
pip install -r requirements.txt
```

### 2. Run Tests
```bash
cd ai-service
python -m pytest
```

### 3. Start the Server
```bash
cd ai-service
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

The service will run on `http://localhost:8000`.
Visit `http://localhost:8000/docs` to test endpoints via Swagger UI.
