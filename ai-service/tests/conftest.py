"""Pytest configuration for TruthGuard AI service test suite."""

import os
import pytest

# Ensure unit tests execute with mock provider and embedding test fallback enabled
os.environ["EMBEDDINGS_FALLBACK"] = "true"
os.environ["LLM_PROVIDER"] = "mock"
os.environ["AI_SERVICE_ENV"] = "test"
