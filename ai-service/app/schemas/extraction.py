"""Schemas for text extraction."""

from typing import Any, Dict
from pydantic import BaseModel, Field


class TextExtractionResponse(BaseModel):
    filename: str = Field(..., description="Original filename")
    format: str = Field(..., description="Detected document format (TXT, PDF, DOCX)")
    extracted_text: str = Field(..., description="Extracted textual content")
    char_count: int = Field(..., description="Character count of extracted text")
    word_count: int = Field(..., description="Word count of extracted text")
    content_hash: str = Field(..., description="SHA-256 hash of extracted text")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="Document metadata such as page count")
