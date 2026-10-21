"""PostgreSQL + pgvector storage engine with idempotent transactional chunk management."""

import json
import logging
from typing import Any, Dict, List, Optional, Tuple
from uuid import UUID

from app.config import settings
from app.schemas.indexing import DocumentChunk, IndexingStatus
from app.services.lexical import LexicalScorer

logger = logging.getLogger(__name__)


class VectorStore:
    """
    Persistent storage engine for document chunks and vector embeddings.
    Supports PostgreSQL with pgvector, with automatic in-memory fallback for offline/test environments.
    """

    _shared_memory_chunks: Dict[UUID, List[DocumentChunk]] = {}
    _shared_memory_status: Dict[UUID, Dict[str, Any]] = {}

    def __init__(
        self,
        database_url: Optional[str] = None,
        force_in_memory: bool = False,
        isolate_memory: bool = False
    ):
        self.database_url = database_url or settings.database_url
        self.force_in_memory = force_in_memory
        self._is_postgres_available = False

        if isolate_memory:
            self._memory_chunks = {}
            self._memory_status = {}
        else:
            self._memory_chunks = VectorStore._shared_memory_chunks
            self._memory_status = VectorStore._shared_memory_status

        if not self.force_in_memory:
            self._check_postgres_connection()

    def _check_postgres_connection(self):
        """Check if PostgreSQL with pgvector is reachable."""
        try:
            import psycopg
            from pgvector.psycopg import register_vector

            with psycopg.connect(self.database_url, connect_timeout=2) as conn:
                register_vector(conn)
                self._is_postgres_available = True
                logger.info("Successfully connected to PostgreSQL with pgvector.")
                self.init_schema(conn)
        except Exception as e:
            logger.info("PostgreSQL unreachable (%s); using in-memory vector store.", e)
            self._is_postgres_available = False

    def init_schema(self, conn=None):
        """Initialize pgvector extension and document_chunks table."""
        if not self._is_postgres_available:
            return

        schema_sql = f"""
        CREATE EXTENSION IF NOT EXISTS vector;

        CREATE TABLE IF NOT EXISTS document_chunks (
            id UUID PRIMARY KEY,
            document_id UUID NOT NULL,
            version_id UUID NOT NULL,
            chunk_index INTEGER NOT NULL,
            chunk_text TEXT NOT NULL,
            embedding vector({settings.embedding_dimension}),
            page_number INTEGER,
            source_title VARCHAR(255),
            metadata JSONB DEFAULT '{{}}'::jsonb,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT uq_version_chunk_index UNIQUE (version_id, chunk_index)
        );

        CREATE TABLE IF NOT EXISTS indexing_status (
            version_id UUID PRIMARY KEY,
            document_id UUID NOT NULL,
            status VARCHAR(50) NOT NULL,
            chunk_count INTEGER DEFAULT 0,
            embedding_model VARCHAR(100) NOT NULL,
            embedding_dimension INTEGER NOT NULL,
            error_message TEXT,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        );

        CREATE INDEX IF NOT EXISTS idx_chunks_version_id ON document_chunks(version_id);
        CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON document_chunks(document_id);
        """
        try:
            import psycopg
            if conn:
                with conn.cursor() as cur:
                    cur.execute(schema_sql)
                conn.commit()
            else:
                with psycopg.connect(self.database_url) as direct_conn:
                    with direct_conn.cursor() as cur:
                        cur.execute(schema_sql)
                    direct_conn.commit()
            logger.info("Initialized document_chunks and indexing_status schema in PostgreSQL.")
        except Exception as e:
            logger.error("Failed to initialize database schema: %s", e)

    def store_chunks_idempotent(
        self,
        document_id: UUID,
        version_id: UUID,
        chunks: List[DocumentChunk],
        embedding_model: str,
        embedding_dimension: int
    ) -> int:
        """
        Idempotently store chunks for a version:
        Deletes any prior chunks for this version and inserts new ones in a single transaction.
        """
        if self._is_postgres_available:
            return self._store_postgres_idempotent(
                document_id, version_id, chunks, embedding_model, embedding_dimension
            )
        return self._store_memory_idempotent(
            document_id, version_id, chunks, embedding_model, embedding_dimension
        )

    def _store_postgres_idempotent(
        self,
        document_id: UUID,
        version_id: UUID,
        chunks: List[DocumentChunk],
        embedding_model: str,
        embedding_dimension: int
    ) -> int:
        import psycopg
        from pgvector.psycopg import register_vector

        with psycopg.connect(self.database_url) as conn:
            register_vector(conn)
            with conn.transaction():
                with conn.cursor() as cur:
                    # 1. Idempotency: Remove existing chunks for this version
                    cur.execute("DELETE FROM document_chunks WHERE version_id = %s", (version_id,))

                    # 2. Insert new chunks
                    for chunk in chunks:
                        cur.execute(
                            """
                            INSERT INTO document_chunks (
                                id, document_id, version_id, chunk_index, chunk_text,
                                embedding, page_number, source_title, metadata
                            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                            """,
                            (
                                chunk.id,
                                chunk.document_id,
                                chunk.version_id,
                                chunk.chunk_index,
                                chunk.chunk_text,
                                chunk.embedding,
                                chunk.page_number,
                                chunk.source_title,
                                json.dumps(chunk.metadata)
                            )
                        )

                    # 3. Update indexing status
                    cur.execute(
                        """
                        INSERT INTO indexing_status (
                            version_id, document_id, status, chunk_count,
                            embedding_model, embedding_dimension, error_message, updated_at
                        ) VALUES (%s, %s, %s, %s, %s, %s, NULL, CURRENT_TIMESTAMP)
                        ON CONFLICT (version_id) DO UPDATE SET
                            status = EXCLUDED.status,
                            chunk_count = EXCLUDED.chunk_count,
                            embedding_model = EXCLUDED.embedding_model,
                            embedding_dimension = EXCLUDED.embedding_dimension,
                            error_message = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        """,
                        (
                            version_id,
                            document_id,
                            IndexingStatus.INDEXED.value,
                            len(chunks),
                            embedding_model,
                            embedding_dimension
                        )
                    )

        logger.info("Persisted %d chunks for version %s in PostgreSQL", len(chunks), version_id)
        return len(chunks)

    def _store_memory_idempotent(
        self,
        document_id: UUID,
        version_id: UUID,
        chunks: List[DocumentChunk],
        embedding_model: str,
        embedding_dimension: int
    ) -> int:
        # Atomic replacement in memory dictionary
        self._memory_chunks[version_id] = list(chunks)
        self._memory_status[version_id] = {
            "document_id": document_id,
            "version_id": version_id,
            "status": IndexingStatus.INDEXED,
            "chunk_count": len(chunks),
            "embedding_model": embedding_model,
            "embedding_dimension": embedding_dimension,
            "error_message": None
        }
        logger.info("Persisted %d chunks for version %s in memory store", len(chunks), version_id)
        return len(chunks)

    def record_indexing_failure(
        self,
        document_id: UUID,
        version_id: UUID,
        error_message: str,
        embedding_model: str,
        embedding_dimension: int
    ):
        """Record FAILED indexing status."""
        if self._is_postgres_available:
            try:
                import psycopg
                with psycopg.connect(self.database_url) as conn:
                    with conn.transaction():
                        with conn.cursor() as cur:
                            cur.execute(
                                """
                                INSERT INTO indexing_status (
                                    version_id, document_id, status, chunk_count,
                                    embedding_model, embedding_dimension, error_message, updated_at
                                ) VALUES (%s, %s, %s, 0, %s, %s, %s, CURRENT_TIMESTAMP)
                                ON CONFLICT (version_id) DO UPDATE SET
                                    status = EXCLUDED.status,
                                    chunk_count = 0,
                                    embedding_model = EXCLUDED.embedding_model,
                                    embedding_dimension = EXCLUDED.embedding_dimension,
                                    error_message = EXCLUDED.error_message,
                                    updated_at = CURRENT_TIMESTAMP
                                """,
                                (
                                    version_id,
                                    document_id,
                                    IndexingStatus.FAILED.value,
                                    embedding_model,
                                    embedding_dimension,
                                    error_message
                                )
                            )
                return
            except Exception as e:
                logger.error("Failed to persist indexing failure in PostgreSQL: %s", e)

        self._memory_status[version_id] = {
            "document_id": document_id,
            "version_id": version_id,
            "status": IndexingStatus.FAILED,
            "chunk_count": 0,
            "embedding_model": embedding_model,
            "embedding_dimension": embedding_dimension,
            "error_message": error_message
        }

    def get_chunks_by_version(self, version_id: UUID) -> List[DocumentChunk]:
        """Retrieve all stored chunks for a document version."""
        if self._is_postgres_available:
            try:
                import psycopg
                from pgvector.psycopg import register_vector
                with psycopg.connect(self.database_url) as conn:
                    register_vector(conn)
                    with conn.cursor() as cur:
                        cur.execute(
                            """
                            SELECT id, document_id, version_id, chunk_index, chunk_text,
                                   embedding, page_number, source_title, metadata
                            FROM document_chunks
                            WHERE version_id = %s
                            ORDER BY chunk_index ASC
                            """,
                            (version_id,)
                        )
                        rows = cur.fetchall()
                        chunks = []
                        for r in rows:
                            meta = r[8] if isinstance(r[8], dict) else json.loads(r[8] or "{}")
                            chunks.append(DocumentChunk(
                                id=r[0],
                                document_id=r[1],
                                version_id=r[2],
                                chunk_index=r[3],
                                chunk_text=r[4],
                                embedding=list(r[5]) if r[5] is not None else None,
                                page_number=r[6],
                                source_title=r[7],
                                metadata=meta
                            ))
                        return chunks
            except Exception as e:
                logger.error("Failed to query chunks from PostgreSQL: %s", e)

        return list(self._memory_chunks.get(version_id, []))

    def get_indexing_status(self, version_id: UUID) -> Optional[Dict[str, Any]]:
        """Retrieve indexing status for a document version."""
        if self._is_postgres_available:
            try:
                import psycopg
                with psycopg.connect(self.database_url) as conn:
                    with conn.cursor() as cur:
                        cur.execute(
                            """
                            SELECT version_id, document_id, status, chunk_count,
                                   embedding_model, embedding_dimension, error_message, updated_at
                            FROM indexing_status
                            WHERE version_id = %s
                            """,
                            (version_id,)
                        )
                        r = cur.fetchone()
                        if r:
                            return {
                                "version_id": r[0],
                                "document_id": r[1],
                                "status": IndexingStatus(r[2]),
                                "chunk_count": r[3],
                                "embedding_model": r[4],
                                "embedding_dimension": r[5],
                                "error_message": r[6],
                                "updated_at": str(r[7])
                            }
            except Exception as e:
                logger.error("Failed to query status from PostgreSQL: %s", e)

        return self._memory_status.get(version_id)

    def search_candidates(
        self,
        query_text: str,
        query_vector: Optional[List[float]] = None,
        document_ids: Optional[List[UUID]] = None,
        version_ids: Optional[List[UUID]] = None,
        source_types: Optional[List[str]] = None,
        authority_levels: Optional[List[str]] = None,
        effective_date_from: Optional[str] = None,
        effective_date_to: Optional[str] = None,
        include_superseded: bool = True,
        limit: int = 100
    ) -> List[Dict[str, Any]]:
        """
        Retrieve candidate chunks with semantic vector similarity and lexical scores,
        preserving complete version provenance.
        """
        if self._is_postgres_available:
            return self._search_postgres_candidates(
                query_text=query_text,
                query_vector=query_vector,
                document_ids=document_ids,
                version_ids=version_ids,
                source_types=source_types,
                authority_levels=authority_levels,
                effective_date_from=effective_date_from,
                effective_date_to=effective_date_to,
                include_superseded=include_superseded,
                limit=limit
            )

        return self._search_memory_candidates(
            query_text=query_text,
            query_vector=query_vector,
            document_ids=document_ids,
            version_ids=version_ids,
            source_types=source_types,
            authority_levels=authority_levels,
            effective_date_from=effective_date_from,
            effective_date_to=effective_date_to,
            include_superseded=include_superseded,
            limit=limit
        )

    def _search_postgres_candidates(
        self,
        query_text: str,
        query_vector: Optional[List[float]],
        document_ids: Optional[List[UUID]],
        version_ids: Optional[List[UUID]],
        source_types: Optional[List[str]],
        authority_levels: Optional[List[str]],
        effective_date_from: Optional[str],
        effective_date_to: Optional[str],
        include_superseded: bool,
        limit: int
    ) -> List[Dict[str, Any]]:
        import psycopg
        from pgvector.psycopg import register_vector

        results = []
        try:
            with psycopg.connect(self.database_url) as conn:
                register_vector(conn)
                with conn.cursor() as cur:
                    where_clauses = ["1=1"]
                    params: List[Any] = []

                    if query_vector is not None:
                        sem_expr = "1 - (dc.embedding <=> %s::vector)"
                        params.append(query_vector)
                    else:
                        sem_expr = "0.0"

                    params.append(query_text)

                    if document_ids:
                        where_clauses.append("dc.document_id = ANY(%s)")
                        params.append([str(d) for d in document_ids])

                    if version_ids:
                        where_clauses.append("dc.version_id = ANY(%s)")
                        params.append([str(v) for v in version_ids])

                    if source_types:
                        where_clauses.append("(d.source_type = ANY(%s) OR dc.metadata->>'source_type' = ANY(%s))")
                        params.append(source_types)
                        params.append(source_types)

                    if authority_levels:
                        where_clauses.append("(dv.authority_level = ANY(%s) OR dc.metadata->>'authority_level' = ANY(%s))")
                        params.append(authority_levels)
                        params.append(authority_levels)

                    if effective_date_from:
                        where_clauses.append("(dv.effective_date >= %s OR (dc.metadata->>'effective_date')::date >= %s)")
                        params.append(effective_date_from)
                        params.append(effective_date_from)

                    if effective_date_to:
                        where_clauses.append("(dv.effective_date <= %s OR (dc.metadata->>'effective_date')::date <= %s)")
                        params.append(effective_date_to)
                        params.append(effective_date_to)

                    if not include_superseded:
                        where_clauses.append("(dv.superseded_by_id IS NULL AND (dc.metadata->>'superseded_by_id' IS NULL))")

                    where_sql = " AND ".join(where_clauses)
                    sql = f"""
                    SELECT
                        dc.id, dc.document_id, dc.version_id, dc.chunk_index, dc.chunk_text,
                        dc.page_number, dc.source_title, dc.metadata,
                        {sem_expr} AS semantic_score,
                        ts_rank_cd(to_tsvector('english', dc.chunk_text), plainto_tsquery('english', %s)) AS fts_score,
                        dv.version_identifier, dv.upload_timestamp, dv.effective_date,
                        dv.authority_level, dv.supersedes_version_id, dv.superseded_by_id,
                        d.source_type
                    FROM document_chunks dc
                    LEFT JOIN document_versions dv ON dc.version_id = dv.id
                    LEFT JOIN documents d ON dc.document_id = d.id
                    WHERE {where_sql}
                    LIMIT %s
                    """
                    params.append(limit)

                    cur.execute(sql, tuple(params))
                    rows = cur.fetchall()

                    for r in rows:
                        meta = r[7] if isinstance(r[7], dict) else json.loads(r[7] or "{}")
                        chunk = DocumentChunk(
                            id=r[0],
                            document_id=r[1],
                            version_id=r[2],
                            chunk_index=r[3],
                            chunk_text=r[4],
                            page_number=r[5],
                            source_title=r[6],
                            metadata=meta
                        )
                        raw_sem = float(r[8]) if r[8] is not None else 0.0
                        raw_fts = float(r[9]) if r[9] is not None else 0.0

                        # Also run lexical scorer for exact token/phrase density alignment
                        py_lexical = LexicalScorer.score(query_text, chunk.chunk_text)
                        keyword_score = max(min(1.0, raw_fts * 2.0), py_lexical)

                        # Version provenance fallback to chunk metadata
                        v_ident = r[10] or meta.get("version_identifier")
                        u_time = str(r[11]) if r[11] else meta.get("upload_timestamp")
                        e_date = str(r[12]) if r[12] else meta.get("effective_date")
                        auth = r[13] or meta.get("authority_level")
                        s_older = r[14] or meta.get("supersedes_version_id")
                        s_newer = r[15] or meta.get("superseded_by_id")
                        s_type = r[16] or meta.get("source_type")

                        results.append({
                            "chunk": chunk,
                            "semantic_score": raw_sem,
                            "keyword_score": keyword_score,
                            "version_identifier": v_ident,
                            "source_type": s_type,
                            "effective_date": e_date,
                            "upload_timestamp": u_time,
                            "authority_level": auth,
                            "supersedes_version_id": UUID(str(s_older)) if s_older else None,
                            "superseded_by_id": UUID(str(s_newer)) if s_newer else None,
                        })
        except Exception as e:
            logger.error("PostgreSQL candidate search failed: %s; falling back to in-memory search", e)
            return self._search_memory_candidates(
                query_text, query_vector, document_ids, version_ids,
                source_types, authority_levels, effective_date_from,
                effective_date_to, include_superseded, limit
            )

        return results

    def _search_memory_candidates(
        self,
        query_text: str,
        query_vector: Optional[List[float]],
        document_ids: Optional[List[UUID]],
        version_ids: Optional[List[UUID]],
        source_types: Optional[List[str]],
        authority_levels: Optional[List[str]],
        effective_date_from: Optional[str],
        effective_date_to: Optional[str],
        include_superseded: bool,
        limit: int
    ) -> List[Dict[str, Any]]:
        results = []
        all_chunks: List[DocumentChunk] = [c for c_list in self._memory_chunks.values() for c in c_list]

        for chunk in all_chunks:
            meta = chunk.metadata or {}

            # Filter checks
            if document_ids and chunk.document_id not in document_ids:
                continue
            if version_ids and chunk.version_id not in version_ids:
                continue

            chunk_source_type = meta.get("source_type")
            if source_types and chunk_source_type not in source_types:
                continue

            chunk_authority = meta.get("authority_level")
            if authority_levels and chunk_authority not in authority_levels:
                continue

            chunk_eff_date = meta.get("effective_date")
            if effective_date_from and chunk_eff_date and str(chunk_eff_date) < str(effective_date_from):
                continue
            if effective_date_to and chunk_eff_date and str(chunk_eff_date) > str(effective_date_to):
                continue

            chunk_superseded_by = meta.get("superseded_by_id")
            if not include_superseded and chunk_superseded_by:
                continue

            # Semantic cosine similarity
            sem_score = 0.0
            if query_vector is not None and chunk.embedding is not None:
                sem_score = sum(a * b for a, b in zip(query_vector, chunk.embedding))

            # Lexical keyword score
            kw_score = LexicalScorer.score(query_text, chunk.chunk_text)

            v_ident = meta.get("version_identifier")
            u_time = meta.get("upload_timestamp")
            auth = meta.get("authority_level")
            s_older = meta.get("supersedes_version_id")
            s_newer = meta.get("superseded_by_id")
            s_type = meta.get("source_type")

            results.append({
                "chunk": chunk,
                "semantic_score": sem_score,
                "keyword_score": kw_score,
                "version_identifier": v_ident,
                "source_type": s_type,
                "effective_date": chunk_eff_date,
                "upload_timestamp": u_time,
                "authority_level": auth,
                "supersedes_version_id": UUID(str(s_older)) if s_older else None,
                "superseded_by_id": UUID(str(s_newer)) if s_newer else None,
            })

        return results
