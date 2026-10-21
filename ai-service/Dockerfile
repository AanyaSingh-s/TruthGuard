# Python AI Service Dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install curl for healthchecks and build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download real sentence-transformers embedding model weights at build time
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')"

COPY app/ ./app
COPY README.md .

EXPOSE 8000

ENV AI_SERVICE_HOST=0.0.0.0
ENV AI_SERVICE_PORT=8000
ENV EMBEDDING_MODEL_NAME=sentence-transformers/all-MiniLM-L6-v2
ENV EMBEDDINGS_FALLBACK=false

HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=5 \
  CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
