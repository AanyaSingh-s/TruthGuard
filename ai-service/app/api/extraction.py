"""Text extraction API endpoints."""

import logging
from fastapi import APIRouter, File, HTTPException, UploadFile, status

from app.schemas.extraction import TextExtractionResponse
from app.services.extractor import (
    EmptyDocumentError,
    MalformedDocumentError,
    UnsupportedFormatError,
    compute_sha256,
    extract_text,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1", tags=["Ingestion & Extraction"])


@router.post(
    "/extract-text",
    response_model=TextExtractionResponse,
    summary="Extract clean text from uploaded document",
    description="Extracts normalized text from TXT, PDF, or DOCX documents with metadata and SHA-256 hash."
)
async def extract_document_text(file: UploadFile = File(...)) -> TextExtractionResponse:
    if not file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "InvalidFilename", "message": "Uploaded file must have a valid filename"}
        )

    content_bytes = await file.read()
    if not content_bytes or len(content_bytes) == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "EmptyFileError", "message": f"Uploaded file '{file.filename}' is empty (0 bytes)"}
        )

    try:
        detected_format, text, metadata = extract_text(file.filename, content_bytes)
        content_hash = compute_sha256(text)
        words = len(text.split())

        logger.info(
            "Extracted %d chars (%d words) from %s [%s]",
            len(text), words, file.filename, detected_format
        )

        return TextExtractionResponse(
            filename=file.filename,
            format=detected_format,
            extracted_text=text,
            char_count=len(text),
            word_count=words,
            content_hash=content_hash,
            metadata=metadata
        )

    except UnsupportedFormatError as e:
        logger.warning("Unsupported file format attempted: %s", file.filename)
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail={"error": "UnsupportedFormatError", "message": str(e)}
        )
    except EmptyDocumentError as e:
        logger.warning("Empty document content in %s: %s", file.filename, e)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail={"error": "EmptyDocumentError", "message": str(e)}
        )
    except MalformedDocumentError as e:
        logger.error("Malformed document parsing %s: %s", file.filename, e)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail={"error": "MalformedDocumentError", "message": str(e)}
        )
    except Exception as e:
        logger.error("Unexpected error during text extraction of %s: %s", file.filename, e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "ExtractionServerError", "message": f"Server failed to process file: {str(e)}"}
        )
