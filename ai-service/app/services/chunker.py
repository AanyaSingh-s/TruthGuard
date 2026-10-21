"""Text chunking engine with configurable window, overlap, and metadata preservation."""

import logging
from typing import Any, Dict, List, Optional, Tuple
from uuid import UUID, uuid4

from app.config import settings
from app.schemas.indexing import DocumentChunk, PageContent

logger = logging.getLogger(__name__)


class TextChunker:
    """Splits document text into overlapping segments preserving provenance and metadata."""

    def __init__(self, default_chunk_size: Optional[int] = None, default_chunk_overlap: Optional[int] = None):
        self.default_chunk_size = default_chunk_size or settings.default_chunk_size
        self.default_chunk_overlap = default_chunk_overlap or settings.default_chunk_overlap

    def split_raw_text(
        self,
        text: str,
        chunk_size: Optional[int] = None,
        chunk_overlap: Optional[int] = None
    ) -> List[Tuple[str, int, int]]:
        """
        Split a string into overlapping chunks.
        Returns: List of tuples (chunk_text, char_start, char_end)
        """
        if not text or not text.strip():
            return []

        c_size = chunk_size if chunk_size is not None else self.default_chunk_size
        c_overlap = chunk_overlap if chunk_overlap is not None else self.default_chunk_overlap

        if c_size <= 0:
            raise ValueError(f"chunk_size must be positive, got {c_size}")
        if c_overlap < 0:
            raise ValueError(f"chunk_overlap cannot be negative, got {c_overlap}")
        if c_overlap >= c_size:
            raise ValueError(f"chunk_overlap ({c_overlap}) must be strictly less than chunk_size ({c_size})")

        normalized = text.strip()
        text_len = len(normalized)
        chunks: List[Tuple[str, int, int]] = []
        start = 0

        while start < text_len:
            end = start + c_size
            if end >= text_len:
                chunk_str = normalized[start:].strip()
                if chunk_str:
                    chunks.append((chunk_str, start, text_len))
                break

            # Search backwards from target end to find clean sentence, paragraph, or word break
            cut = -1
            separators = (". ", "?\n", "!\n", ".\n", "? ", "! ", "\n\n", "\n", " ")
            for sep in separators:
                pos = normalized.rfind(sep, start + c_overlap, end)
                if pos != -1:
                    cut = pos + len(sep)
                    break

            # If no separator was found in the search window, cut strictly at end
            if cut == -1 or cut <= start:
                cut = end

            chunk_str = normalized[start:cut].strip()
            if chunk_str:
                chunks.append((chunk_str, start, cut))

            # Advance window by step (chunk_size - overlap)
            next_start = cut - c_overlap
            if next_start <= start:
                next_start = cut
            start = next_start

        return chunks

    def chunk_version(
        self,
        document_id: UUID,
        version_id: UUID,
        text: str,
        source_title: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None,
        chunk_size: Optional[int] = None,
        chunk_overlap: Optional[int] = None,
        pages: Optional[List[PageContent]] = None
    ) -> List[DocumentChunk]:
        """
        Produces fully initialized DocumentChunk instances with preserved metadata.
        """
        base_meta = dict(metadata or {})
        chunks: List[DocumentChunk] = []
        chunk_index = 0

        # Scenario A: Explicit page segments provided (e.g. from PDF extraction)
        if pages and len(pages) > 0:
            for page in pages:
                page_chunks = self.split_raw_text(page.text, chunk_size, chunk_overlap)
                for chunk_text, char_start, char_end in page_chunks:
                    chunk_meta = {
                        **base_meta,
                        "page_number": page.page_number,
                        "char_start": char_start,
                        "char_end": char_end,
                        "char_count": len(chunk_text),
                        "word_count": len(chunk_text.split()),
                    }
                    chunks.append(DocumentChunk(
                        id=uuid4(),
                        document_id=document_id,
                        version_id=version_id,
                        chunk_index=chunk_index,
                        chunk_text=chunk_text,
                        page_number=page.page_number,
                        source_title=source_title,
                        metadata=chunk_meta
                    ))
                    chunk_index += 1
            return chunks

        # Scenario B: Form feed '\f' character page separators in raw text
        if "\f" in text:
            raw_pages = text.split("\f")
            for page_idx, page_str in enumerate(raw_pages, start=1):
                page_chunks = self.split_raw_text(page_str, chunk_size, chunk_overlap)
                for chunk_text, char_start, char_end in page_chunks:
                    chunk_meta = {
                        **base_meta,
                        "page_number": page_idx,
                        "char_start": char_start,
                        "char_end": char_end,
                        "char_count": len(chunk_text),
                        "word_count": len(chunk_text.split()),
                    }
                    chunks.append(DocumentChunk(
                        id=uuid4(),
                        document_id=document_id,
                        version_id=version_id,
                        chunk_index=chunk_index,
                        chunk_text=chunk_text,
                        page_number=page_idx,
                        source_title=source_title,
                        metadata=chunk_meta
                    ))
                    chunk_index += 1
            return chunks

        # Scenario C: Monolithic plain text
        raw_chunks = self.split_raw_text(text, chunk_size, chunk_overlap)
        for chunk_text, char_start, char_end in raw_chunks:
            chunk_meta = {
                **base_meta,
                "char_start": char_start,
                "char_end": char_end,
                "char_count": len(chunk_text),
                "word_count": len(chunk_text.split()),
            }
            chunks.append(DocumentChunk(
                id=uuid4(),
                document_id=document_id,
                version_id=version_id,
                chunk_index=chunk_index,
                chunk_text=chunk_text,
                page_number=None,
                source_title=source_title,
                metadata=chunk_meta
            ))
            chunk_index += 1

        logger.info(
            "Chunked version %s into %d chunks (chunk_size=%d, overlap=%d)",
            version_id, len(chunks),
            chunk_size or self.default_chunk_size,
            chunk_overlap or self.default_chunk_overlap
        )
        return chunks
