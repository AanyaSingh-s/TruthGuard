"""Unit and integration tests for AI Service health endpoints."""

import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health_endpoint_returns_200():
    """Verify GET /health returns HTTP 200 and schema compliant payload."""
    response = client.get("/health")
    assert response.status_code == 200

    data = response.json()
    assert data["status"] == "UP"
    assert data["service"] == "truthguard-ai-service"
    assert data["version"] == "0.1.0"
    assert "environment" in data
    assert "timestamp" in data
    assert "details" in data
    assert "role" in data["details"]


def test_v1_health_alias():
    """Verify GET /api/v1/health returns HTTP 200 as an alias."""
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["service"] == "truthguard-ai-service"


def test_cors_headers():
    """Verify CORS headers allow cross-origin requests from frontend."""
    response = client.get("/health", headers={"Origin": "http://localhost:5173"})
    assert response.status_code == 200
    assert response.headers.get("access-control-allow-origin") == "*"
