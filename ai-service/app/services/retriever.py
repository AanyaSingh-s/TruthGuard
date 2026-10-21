"""TruthGuard Retriever Agent coordinating dense semantic search and lexical matching."""

import logging
from typing import Any, Dict, List, Optional
from uuid import UUID

from app.config import settings
from app.db.vector_store import VectorStore
from app.schemas.retrieval import (
    RetrievalFilter,
    RetrievalRequest,
    RetrievalResponse,
    RetrievedPassage,
)
from app.services.embedder import EmbeddingService

logger = logging.getLogger(__name__)


class RetrieverService:
    """
    Typed retrieval service combining semantic vector similarity with lexical keyword matching.
    Preserves all provenance metadata and never discards conflicting document versions.
    """

    def __init__(
        self,
        embedder: Optional[EmbeddingService] = None,
        store: Optional[VectorStore] = None
    ):
        self.embedder = embedder or EmbeddingService()
        self.store = store or VectorStore()

    def retrieve(self, request: RetrievalRequest) -> RetrievalResponse:
        """
        Execute hybrid retrieval for a question:
        1. Validates query input.
        2. Embeds question using the system embedding model.
        3. Retrieves candidate passages from pgvector / in-memory store.
        4. Fuses dense semantic similarity and keyword scores using documented ranking.
        5. Preserves all version provenance, effective dates, and authority levels.
        6. Enforces top_k and minimum relevance thresholds without dropping conflicting evidence.
        """
        clean_question = request.question.strip()
        if not clean_question:
            raise ValueError("Question cannot be empty or whitespace-only")

        logger.info(
            "Executing retrieval for question: '%s' (top_k=%d, min_score=%.2f, alpha=%.2f, method=%s)",
            clean_question, request.top_k, request.min_score, request.semantic_weight, request.ranking_method
        )

        # 1. Embed question vector
        query_vector = self.embedder.embed_text(clean_question)

        # 2. Extract filter criteria
        filters = request.filters or RetrievalFilter()
        doc_ids = filters.document_ids
        ver_ids = filters.version_ids
        source_types = filters.source_types
        authority_levels = filters.authority_levels
        eff_from = str(filters.effective_date_from) if filters.effective_date_from else None
        eff_to = str(filters.effective_date_to) if filters.effective_date_to else None
        include_superseded = filters.include_superseded

        # 3. Retrieve raw candidate pool
        candidates = self.store.search_candidates(
            query_text=clean_question,
            query_vector=query_vector,
            document_ids=doc_ids,
            version_ids=ver_ids,
            source_types=source_types,
            authority_levels=authority_levels,
            effective_date_from=eff_from,
            effective_date_to=eff_to,
            include_superseded=include_superseded,
            limit=max(100, request.top_k * 5)
        )

        if not candidates:
            logger.info("No candidate passages found matching query and filters")
            return RetrievalResponse(
                question=clean_question,
                total_retrieved=0,
                passages=[],
                retrieval_metadata={
                    "embedding_model": self.embedder.model_name,
                    "ranking_method": request.ranking_method,
                    "semantic_weight": request.semantic_weight,
                    "top_k": request.top_k,
                    "min_score": request.min_score,
                    "candidates_scanned": 0
                }
            )

        # 4. Score and Rank Candidates
        scored_passages = self._rank_candidates(
            candidates=candidates,
            alpha=request.semantic_weight,
            ranking_method=request.ranking_method,
            min_score=request.min_score
        )

        # 5. Enforce top_k while preserving distinct version evidence
        final_passages = scored_passages[:request.top_k]

        logger.info(
            "Retrieved %d ranked passages for question '%s' (scanned %d candidates)",
            len(final_passages), clean_question, len(candidates)
        )

        return RetrievalResponse(
            question=clean_question,
            total_retrieved=len(final_passages),
            passages=final_passages,
            retrieval_metadata={
                "embedding_model": self.embedder.model_name,
                "embedding_dimension": self.embedder.dimension,
                "ranking_method": request.ranking_method,
                "semantic_weight": request.semantic_weight,
                "top_k": request.top_k,
                "min_score": request.min_score,
                "candidates_scanned": len(candidates),
                "include_superseded": include_superseded
            }
        )

    def _rank_candidates(
        self,
        candidates: List[Dict[str, Any]],
        alpha: float,
        ranking_method: str,
        min_score: float
    ) -> List[RetrievedPassage]:
        """
        Combine dense semantic and lexical scores according to the documented ranking strategy.
        """
        if ranking_method == "rrf":
            return self._rank_reciprocal_rank_fusion(candidates, alpha, min_score)
        return self._rank_hybrid_linear(candidates, alpha, min_score)

    def _rank_hybrid_linear(
        self,
        candidates: List[Dict[str, Any]],
        alpha: float,
        min_score: float
    ) -> List[RetrievedPassage]:
        """
        Normalized convex combination of dense semantic similarity and keyword scores:
        Score = alpha * max(0.0, semantic_score) + (1 - alpha) * keyword_score
        """
        scored: List[RetrievedPassage] = []

        for item in candidates:
            chunk = item["chunk"]
            raw_sem = item["semantic_score"]
            raw_kw = item["keyword_score"]

            # Normalize semantic cosine similarity from [-1, 1] to [0, 1]
            sem_norm = max(0.0, min(1.0, raw_sem))
            kw_norm = max(0.0, min(1.0, raw_kw))

            combined_score = alpha * sem_norm + (1.0 - alpha) * kw_norm

            if combined_score < min_score:
                continue

            scored.append(RetrievedPassage(
                chunk_id=chunk.id,
                document_id=chunk.document_id,
                version_id=chunk.version_id,
                version_identifier=item.get("version_identifier"),
                chunk_index=chunk.chunk_index,
                text=chunk.chunk_text,
                score=round(combined_score, 4),
                semantic_score=round(raw_sem, 4),
                keyword_score=round(raw_kw, 4),
                source_title=chunk.source_title,
                source_type=item.get("source_type"),
                page_number=chunk.page_number,
                effective_date=item.get("effective_date"),
                upload_timestamp=item.get("upload_timestamp"),
                authority_level=item.get("authority_level"),
                supersedes_version_id=item.get("supersedes_version_id"),
                superseded_by_id=item.get("superseded_by_id"),
                metadata=chunk.metadata
            ))

        # Sort descending by combined score
        scored.sort(key=lambda p: p.score, reverse=True)
        return scored

    def _rank_reciprocal_rank_fusion(
        self,
        candidates: List[Dict[str, Any]],
        alpha: float,
        min_score: float,
        k: int = 60
    ) -> List[RetrievedPassage]:
        """
        Reciprocal Rank Fusion (RRF):
        RRF_score(d) = alpha / (k + rank_semantic) + (1 - alpha) / (k + rank_keyword)
        Scaled to [0, 1] for threshold consistency with min_score.
        """
        # Assign semantic ranks
        by_semantic = sorted(candidates, key=lambda c: c["semantic_score"], reverse=True)
        sem_ranks = {id(c): rank for rank, c in enumerate(by_semantic, start=1)}

        # Assign keyword ranks
        by_keyword = sorted(candidates, key=lambda c: c["keyword_score"], reverse=True)
        kw_ranks = {id(c): rank for rank, c in enumerate(by_keyword, start=1)}

        # Normalization factor (ideal rank 1 score)
        max_possible_rrf = (alpha / (k + 1)) + ((1.0 - alpha) / (k + 1))

        scored: List[RetrievedPassage] = []
        for item in candidates:
            chunk = item["chunk"]
            cid = id(item)
            r_sem = sem_ranks[cid]
            r_kw = kw_ranks[cid]

            rrf_raw = (alpha / (k + r_sem)) + ((1.0 - alpha) / (k + r_kw))
            normalized_score = rrf_raw / max_possible_rrf if max_possible_rrf > 0 else 0.0

            if normalized_score < min_score:
                continue

            scored.append(RetrievedPassage(
                chunk_id=chunk.id,
                document_id=chunk.document_id,
                version_id=chunk.version_id,
                version_identifier=item.get("version_identifier"),
                chunk_index=chunk.chunk_index,
                text=chunk.chunk_text,
                score=round(normalized_score, 4),
                semantic_score=round(item["semantic_score"], 4),
                keyword_score=round(item["keyword_score"], 4),
                source_title=chunk.source_title,
                source_type=item.get("source_type"),
                page_number=chunk.page_number,
                effective_date=item.get("effective_date"),
                upload_timestamp=item.get("upload_timestamp"),
                authority_level=item.get("authority_level"),
                supersedes_version_id=item.get("supersedes_version_id"),
                superseded_by_id=item.get("superseded_by_id"),
                metadata=chunk.metadata
            ))

        scored.sort(key=lambda p: p.score, reverse=True)
        return scored
