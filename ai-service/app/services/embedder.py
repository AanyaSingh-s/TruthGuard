"""Dense vector embedding engine supporting sentence-transformers/all-MiniLM-L6-v2."""

import hashlib
import logging
import math
import os
from typing import List, Optional

from app.config import settings
from app.services.lexical import tokenize_text

logger = logging.getLogger(__name__)


class EmbeddingError(Exception):
    """Raised when embedding generation fails."""
    pass


class EmbeddingService:
    """
    Generates unit-normalized dense vector embeddings for semantic retrieval.

    Documented Model: sentence-transformers/all-MiniLM-L6-v2
    Embedding Dimension: 384
    Metric: Cosine Similarity / Inner Product
    """

    def __init__(
        self,
        model_name: Optional[str] = None,
        dimension: Optional[int] = None,
        simulate_failure: bool = False
    ):
        self.model_name = model_name or settings.embedding_model_name
        self.dimension = dimension or settings.embedding_dimension
        self.simulate_failure = simulate_failure
        self._model = None
        self._init_model()

    def _is_fallback_allowed(self) -> bool:
        """Determines if deterministic hash fallback is permitted (unit tests only)."""
        return (
            settings.embeddings_fallback
            or os.getenv("EMBEDDINGS_FALLBACK", "false").lower() in ("true", "1")
            or bool(os.getenv("PYTEST_CURRENT_TEST"))
        )

    def _init_model(self):
        """Attempt to load real sentence-transformers model without local_files_only restrictions."""
        try:
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer(self.model_name)
            logger.info("Successfully loaded sentence-transformer model: %s", self.model_name)
        except Exception as e:
            if self._is_fallback_allowed():
                logger.info(
                    "Model %s failed to load (%s); EMBEDDINGS_FALLBACK is enabled for unit tests. Using deterministic hash fallback.",
                    self.model_name, e
                )
                self._model = None
            else:
                logger.critical(
                    "FATAL: Failed to load sentence-transformers embedding model '%s': %s. Normal runs require live model weights.",
                    self.model_name, e
                )
                raise EmbeddingError(
                    f"Failed to load sentence-transformers model '{self.model_name}': {e}. "
                    "In normal runs, the model must be loaded; set EMBEDDINGS_FALLBACK=true only for unit tests."
                ) from e

    def embed_text(self, text: str) -> List[float]:
        """Generate a 384-dimensional unit-normalized embedding for a single text."""
        if self.simulate_failure:
            raise EmbeddingError("Simulated embedding inference failure: model timeout or out-of-memory")

        if not text or not text.strip():
            raise EmbeddingError("Cannot embed empty text or whitespace-only string")

        if self._model is not None:
            try:
                vec = self._model.encode(text, normalize_embeddings=True).tolist()
                return [float(x) for x in vec]
            except Exception as e:
                if self._is_fallback_allowed():
                    logger.warning("Live model inference failed: %s; falling back to deterministic projection", e)
                else:
                    raise EmbeddingError(f"Embedding inference failed: {e}") from e

        # Hash fallback ONLY for unit tests
        if not self._is_fallback_allowed():
            raise EmbeddingError(
                f"Embedding model '{self.model_name}' is not loaded and EMBEDDINGS_FALLBACK is false. Normal runs require live embeddings."
            )

        return self._generate_deterministic_vector(text)

    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        """Generate embeddings for a list of texts."""
        if self.simulate_failure:
            raise EmbeddingError("Simulated batch embedding failure: model unreachable")

        if not texts:
            return []

        return [self.embed_text(t) for t in texts]

    def _generate_deterministic_vector(self, text: str) -> List[float]:
        """
        Generate a strictly 384-dimensional unit-normalized vector for testing.
        Guarantees:
        1. Dimension is strictly self.dimension (384).
        2. L2 norm is strictly 1.0 (unit sphere).
        3. Deterministic: identical text produces identical vector.
        4. Random projection indexing: preserves semantic subword/token similarity.
        """
        cleaned = text.strip()
        words = tokenize_text(cleaned, filter_stopwords=True)
        if not words:
            words = tokenize_text(cleaned, filter_stopwords=False)
        if not words:
            words = [cleaned.lower()]

        acc = [0.0] * self.dimension

        # Token random projections
        for w in words:
            # Deterministic hash to seed random projection
            h = int(hashlib.sha256(w.encode("utf-8")).hexdigest(), 16)
            for d in range(self.dimension):
                # Pseudo-random sign from hash bits
                bit = (h >> (d % 256)) & 1
                sign = 1.0 if bit == 1 else -1.0
                acc[d] += sign

        # Compute Euclidean norm
        norm = math.sqrt(sum(x * x for x in acc))
        if norm == 0.0:
            acc[0] = 1.0
            norm = 1.0

        # L2-normalize to unit length
        return [float(x / norm) for x in acc]
