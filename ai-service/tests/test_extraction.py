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


def test_extract_synthetic_sample_files():
    """Verify end-to-end extraction against all generated synthetic corpora sample files."""
    import os

    sample_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "sample_documents"))
    if not os.path.exists(sample_dir):
        pytest.skip(f"Synthetic sample documents directory not found at {sample_dir}")

    # 1. Test sample_policy_v1.txt
    with open(os.path.join(sample_dir, "sample_policy_v1.txt"), "rb") as f:
        res = client.post("/api/v1/extract-text", files={"file": ("sample_policy_v1.txt", f.read(), "text/plain")})
        assert res.status_code == 200
        data = res.json()
        assert data["format"] == "TXT"
        assert "multi-factor authentication" in data["extracted_text"]

    # 2. Test sample_policy_v2.txt
    with open(os.path.join(sample_dir, "sample_policy_v2.txt"), "rb") as f:
        res = client.post("/api/v1/extract-text", files={"file": ("sample_policy_v2.txt", f.read(), "text/plain")})
        assert res.status_code == 200
        data = res.json()
        assert data["format"] == "TXT"
        assert "FIDO2" in data["extracted_text"]

    # 3. Test sample_regulation.pdf
    with open(os.path.join(sample_dir, "sample_regulation.pdf"), "rb") as f:
        res = client.post("/api/v1/extract-text", files={"file": ("sample_regulation.pdf", f.read(), "application/pdf")})
        assert res.status_code == 200
        data = res.json()
        assert data["format"] == "PDF"
        assert "Regulatory Compliance Guideline" in data["extracted_text"]

    # 4. Test sample_specification.docx
    with open(os.path.join(sample_dir, "sample_specification.docx"), "rb") as f:
        res = client.post("/api/v1/extract-text", files={"file": ("sample_specification.docx", f.read(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document")})
        assert res.status_code == 200
        data = res.json()
        assert data["format"] == "DOCX"
        assert "System Specification" in data["extracted_text"]

    # 5. Test empty_document.txt -> 400
    with open(os.path.join(sample_dir, "empty_document.txt"), "rb") as f:
        res = client.post("/api/v1/extract-text", files={"file": ("empty_document.txt", f.read(), "text/plain")})
        assert res.status_code == 400

    # 6. Test whitespace_only.txt -> 422
    with open(os.path.join(sample_dir, "whitespace_only.txt"), "rb") as f:
        res = client.post("/api/v1/extract-text", files={"file": ("whitespace_only.txt", f.read(), "text/plain")})
        assert res.status_code == 422

    # 7. Test corrupt_malformed.pdf -> 422
    with open(os.path.join(sample_dir, "corrupt_malformed.pdf"), "rb") as f:
        res = client.post("/api/v1/extract-text", files={"file": ("corrupt_malformed.pdf", f.read(), "application/pdf")})
        assert res.status_code == 422

    # 8. Test unsupported_format.exe -> 415
    with open(os.path.join(sample_dir, "unsupported_format.exe"), "rb") as f:
        res = client.post("/api/v1/extract-text", files={"file": ("unsupported_format.exe", f.read(), "application/octet-stream")})
        assert res.status_code == 415
