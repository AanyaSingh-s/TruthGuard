"""Unit and API tests for document text extraction."""

import io
import pytest
from fastapi.testclient import TestClient
from reportlab.pdfgen import canvas
import docx

from app.main import app

client = TestClient(app)


def create_sample_pdf(text: str) -> bytes:
    """Generate a valid PDF in-memory using reportlab."""
    buffer = io.BytesIO()
    c = canvas.Canvas(buffer)
    c.drawString(100, 750, text)
    c.save()
    buffer.seek(0)
    return buffer.getvalue()


def create_sample_docx(text: str) -> bytes:
    """Generate a valid DOCX in-memory."""
    doc = docx.Document()
    doc.add_paragraph(text)
    buffer = io.BytesIO()
    doc.save(buffer)
    buffer.seek(0)
    return buffer.getvalue()


def test_extract_from_valid_txt():
    """Verify TXT file extraction returns clean text and SHA-256 content hash."""
    sample_text = "TruthGuard Ingestion Specification\nVersion 1.0\nAll claims must be supported."
    files = {"file": ("spec.txt", sample_text.encode("utf-8"), "text/plain")}

    response = client.post("/api/v1/extract-text", files=files)
    assert response.status_code == 200

    data = response.json()
    assert data["format"] == "TXT"
    assert "TruthGuard Ingestion Specification" in data["extracted_text"]
    assert data["char_count"] > 0
    assert data["word_count"] > 0
    assert len(data["content_hash"]) == 64


def test_extract_from_valid_pdf():
    """Verify PDF file extraction using pypdf."""
    pdf_bytes = create_sample_pdf("Policy Document: All actions require cryptographic audit trails.")
    files = {"file": ("policy.pdf", pdf_bytes, "application/pdf")}

    response = client.post("/api/v1/extract-text", files=files)
    assert response.status_code == 200

    data = response.json()
    assert data["format"] == "PDF"
    assert "Policy Document" in data["extracted_text"]
    assert data["metadata"]["page_count"] == 1
    assert len(data["content_hash"]) == 64


def test_extract_from_valid_docx():
    """Verify DOCX file extraction."""
    docx_bytes = create_sample_docx("Architecture Guideline: Service boundaries must remain strictly decoupled.")
    files = {"file": ("guideline.docx", docx_bytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")}

    response = client.post("/api/v1/extract-text", files=files)
    assert response.status_code == 200

    data = response.json()
    assert data["format"] == "DOCX"
    assert "Architecture Guideline" in data["extracted_text"]
    assert data["metadata"]["paragraph_count"] >= 1


def test_reject_empty_file():
    """Verify empty 0-byte file upload returns HTTP 400 Bad Request."""
    files = {"file": ("empty.txt", b"", "text/plain")}
    response = client.post("/api/v1/extract-text", files=files)
    assert response.status_code == 400


def test_reject_unsupported_format():
    """Verify unsupported file extension returns HTTP 415 Unsupported Media Type."""
    files = {"file": ("malicious.exe", b"MZbinarycontent", "application/octet-stream")}
    response = client.post("/api/v1/extract-text", files=files)
    assert response.status_code == 415


def test_reject_malformed_pdf():
    """Verify corrupt/malformed PDF returns HTTP 422 Unprocessable Entity."""
    corrupt_bytes = b"%PDF-1.4 corrupt junk random garbage not a real pdf %%EOF"
    files = {"file": ("corrupt.pdf", corrupt_bytes, "application/pdf")}
    response = client.post("/api/v1/extract-text", files=files)
    assert response.status_code == 422


def test_reject_blank_whitespace_document():
    """Verify document with only whitespace returns HTTP 422 EmptyDocumentError."""
    files = {"file": ("blank.txt", b"   \n\t  \n  ", "text/plain")}
    response = client.post("/api/v1/extract-text", files=files)
    assert response.status_code == 422
