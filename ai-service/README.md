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
| `GET` | `/docs` | OpenAPI / Swagger interactive documentation |
| `GET` | `/redoc` | ReDoc API specification |

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
