"""LLM provider abstraction interface and implementations for TruthGuard.

Supports:
- GeminiProvider (Google Gemini API with responseMimeType="application/json")
- OpenAICompatibleProvider (OpenAI / vLLM / LiteLLM with response_format={"type": "json_object"})
- MockLLMProvider (Strict test mock with NO hardcoded domain logic; callers must inject custom_response or response_generator)
"""

from abc import ABC, abstractmethod
import json
import logging
import os
import re
from typing import Any, Callable, Dict, List, Optional, Tuple
import httpx

from app.config import settings

logger = logging.getLogger(__name__)


class LLMError(Exception):
    """Base exception for LLM provider operations."""
    pass


class LLMTimeoutError(LLMError):
    """Raised when an LLM provider request times out."""
    pass


class LLMProviderError(LLMError):
    """Raised when an LLM provider returns an API error or is unreachable."""
    pass


class MalformedLLMResponseError(LLMError):
    """Raised when an LLM response cannot be parsed into the expected JSON structure."""
    pass


def is_valid_json(text: str) -> bool:
    """Helper verifying if text contains parseable JSON object or array."""
    if not text or not text.strip():
        return False
    t = text.strip()
    if "```" in t:
        match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", t)
        if match:
            t = match.group(1).strip()
    start_brace = t.find("{")
    end_brace = t.rfind("}")
    if start_brace != -1 and end_brace > start_brace:
        try:
            parsed = json.loads(t[start_brace:end_brace + 1])
            return isinstance(parsed, (dict, list))
        except json.JSONDecodeError:
            return False
    return False


class LLMProvider(ABC):
    """Abstract interface decoupling answer synthesis and criticism from specific LLM vendors."""

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Name of the provider vendor (e.g. 'gemini', 'openai', 'mock')."""
        pass

    @property
    @abstractmethod
    def model_name(self) -> str:
        """Configured model identifier (e.g. 'gemini-1.5-flash', 'gpt-4o-mini')."""
        pass

    @abstractmethod
    def generate(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None
    ) -> str:
        """
        Generate raw text response from the language model.
        Must raise LLMTimeoutError on timeout, LLMProviderError on API failure,
        and retry once on malformed JSON before raising MalformedLLMResponseError.
        """
        pass


class GeminiProvider(LLMProvider):
    """
    Google Gemini API provider using the native REST API with strict JSON mode.
    Endpoint: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
    """

    def __init__(
        self,
        model_name: Optional[str] = None,
        api_key: Optional[str] = None,
        timeout_seconds: Optional[int] = None
    ):
        self._model_name = model_name or settings.gemini_model
        self.api_key = api_key or settings.gemini_api_key or settings.llm_api_key
        self.timeout_seconds = timeout_seconds or settings.llm_timeout_seconds
        if not self.api_key:
            raise LLMProviderError(
                "GeminiProvider requires an API key. Set GEMINI_API_KEY or LLM_API_KEY environment variable."
            )

    @property
    def provider_name(self) -> str:
        return "gemini"

    @property
    def model_name(self) -> str:
        return self._model_name

    def _call_gemini_api(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None
    ) -> str:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self._model_name}:generateContent"
        params = {"key": self.api_key}

        body: Dict[str, Any] = {
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": prompt}]
                }
            ],
            "generationConfig": {
                "temperature": temperature if temperature is not None else settings.llm_temperature,
                "maxOutputTokens": max_tokens or settings.llm_max_tokens,
                "responseMimeType": "application/json"
            }
        }

        if system_prompt:
            body["system_instruction"] = {
                "parts": [{"text": system_prompt}]
            }

        headers = {"Content-Type": "application/json"}

        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                res = client.post(url, params=params, json=body, headers=headers)
                if res.status_code >= 400:
                    logger.error("Gemini API error (HTTP %d): %s", res.status_code, res.text)
                    raise LLMProviderError(f"Gemini API returned HTTP {res.status_code}: {res.text}")

                data = res.json()
                candidates = data.get("candidates", [])
                if not candidates:
                    raise LLMProviderError(f"Gemini returned zero response candidates: {data}")

                content = candidates[0].get("content", {})
                parts = content.get("parts", [])
                if not parts or "text" not in parts[0]:
                    raise LLMProviderError(f"Gemini candidate content contains no text parts: {data}")

                return parts[0]["text"]
        except httpx.TimeoutException as e:
            logger.error("Gemini API request timed out after %ds: %s", self.timeout_seconds, e)
            raise LLMTimeoutError(f"Gemini request timed out after {self.timeout_seconds}s: {e}") from e
        except httpx.RequestError as e:
            logger.error("Gemini API network communication failed: %s", e)
            raise LLMProviderError(f"Gemini network failure: {e}") from e

    def generate(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None
    ) -> str:
        """Generates strict JSON, retrying once on malformed output."""
        raw = self._call_gemini_api(prompt, system_prompt, temperature, max_tokens)
        if is_valid_json(raw):
            return raw

        logger.warning("Gemini returned non-JSON response. Retrying once with explicit JSON correction prompt...")
        correction_prompt = (
            f"{prompt}\n\n"
            "CRITICAL CORRECTION REQUIRED: Your prior response was not valid JSON. "
            "You MUST output strictly a single valid JSON object matching the requested schema. "
            "Do not output markdown code blocks or surrounding prose."
        )
        retry_raw = self._call_gemini_api(correction_prompt, system_prompt, temperature, max_tokens)
        if is_valid_json(retry_raw):
            return retry_raw

        raise MalformedLLMResponseError(
            f"Gemini failed to return valid JSON after retry. Received: {retry_raw[:250]}"
        )


class OpenAICompatibleProvider(LLMProvider):
    """
    Provider for OpenAI and OpenAI-compatible endpoints (e.g. vLLM, Ollama, LiteLLM)
    using strict JSON mode (response_format={"type": "json_object"}).
    """

    def __init__(
        self,
        model_name: Optional[str] = None,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        timeout_seconds: Optional[int] = None
    ):
        self._model_name = model_name or settings.openai_model
        self.api_key = api_key or settings.openai_api_key or settings.llm_api_key
        self.base_url = (base_url or settings.openai_base_url).rstrip("/")
        self.timeout_seconds = timeout_seconds or settings.llm_timeout_seconds
        if not self.api_key:
            raise LLMProviderError(
                "OpenAICompatibleProvider requires an API key. Set OPENAI_API_KEY or LLM_API_KEY."
            )

    @property
    def provider_name(self) -> str:
        return "openai"

    @property
    def model_name(self) -> str:
        return self._model_name

    def _call_openai_api(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None
    ) -> str:
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})

        payload = {
            "model": self._model_name,
            "messages": messages,
            "temperature": temperature if temperature is not None else settings.llm_temperature,
            "max_tokens": max_tokens or settings.llm_max_tokens,
            "response_format": {"type": "json_object"}
        }

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                res = client.post(f"{self.base_url}/chat/completions", json=payload, headers=headers)
                if res.status_code >= 400:
                    logger.error("OpenAI API error (HTTP %d): %s", res.status_code, res.text)
                    raise LLMProviderError(f"OpenAI provider returned HTTP {res.status_code}: {res.text}")
                data = res.json()
                return data["choices"][0]["message"]["content"]
        except httpx.TimeoutException as e:
            logger.error("OpenAI request timed out after %ds: %s", self.timeout_seconds, e)
            raise LLMTimeoutError(f"OpenAI request timed out after {self.timeout_seconds}s: {e}") from e
        except httpx.RequestError as e:
            logger.error("OpenAI network request failed: %s", e)
            raise LLMProviderError(f"OpenAI network failure: {e}") from e

    def generate(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None
    ) -> str:
        """Generates strict JSON, retrying once on malformed output."""
        raw = self._call_openai_api(prompt, system_prompt, temperature, max_tokens)
        if is_valid_json(raw):
            return raw

        logger.warning("OpenAI returned non-JSON response. Retrying once with explicit JSON correction prompt...")
        correction_prompt = (
            f"{prompt}\n\n"
            "CRITICAL CORRECTION REQUIRED: Your prior response was not valid JSON. "
            "You MUST output strictly a single valid JSON object matching the requested schema. "
            "Do not output markdown code blocks or surrounding prose."
        )
        retry_raw = self._call_openai_api(correction_prompt, system_prompt, temperature, max_tokens)
        if is_valid_json(retry_raw):
            return retry_raw

        raise MalformedLLMResponseError(
            f"OpenAI provider failed to return valid JSON after retry. Received: {retry_raw[:250]}"
        )


class MockLLMProvider(LLMProvider):
    """
    Mock LLM provider for unit tests and offline sandbox execution.
    STRICT REQUIREMENT: Contains NO hardcoded domain or keyword shortcuts.
    The mock MUST only return what tests explicitly inject via custom_response or response_generator.
    """

    def __init__(
        self,
        model_name: str = "mock-grounded-model",
        custom_response: Optional[str] = None,
        response_generator: Optional[Callable[[str, Optional[str]], str]] = None,
        simulate_timeout: bool = False,
        simulate_error: bool = False,
        simulate_malformed: bool = False
    ):
        self._model_name = model_name
        self.custom_response = custom_response
        self.response_generator = response_generator
        self.simulate_timeout = simulate_timeout
        self.simulate_error = simulate_error
        self.simulate_malformed = simulate_malformed

    @property
    def provider_name(self) -> str:
        return "mock"

    @property
    def model_name(self) -> str:
        return self._model_name

    def generate(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None
    ) -> str:
        if self.simulate_timeout:
            raise LLMTimeoutError("Mock LLM request exceeded 30s timeout limit")

        if self.simulate_error:
            raise LLMProviderError("Mock LLM provider returned 503 Service Unavailable")

        if self.simulate_malformed:
            return "This is non-JSON conversational text: I cannot produce JSON right now."

        if self.custom_response is not None:
            return self.custom_response

        if self.response_generator is not None:
            return self.response_generator(prompt, system_prompt)

        # Fail loudly if neither custom_response nor response_generator was injected
        raise ValueError(
            "MockLLMProvider was called without custom_response or response_generator set. "
            "Tests must explicitly inject responses via custom_response or response_generator. "
            "Hardcoded keyword shortcuts have been removed per specification."
        )


def resolve_provider_type() -> Tuple[str, str, Optional[str]]:
    """
    Determines active provider vendor, model name, and API key.
    Rules:
    1. If an API key is present (GEMINI_API_KEY / OPENAI_API_KEY / LLM_API_KEY),
       default MUST be a real provider (gemini or openai) unless LLM_PROVIDER=mock is explicitly set.
    2. Mock may ONLY be used when LLM_PROVIDER=mock is explicitly set, or during automated test runs.
    3. If provider=mock in a non-test environment, logs a loud warning.
    """
    configured = settings.llm_provider.lower().strip()
    gemini_key = settings.gemini_api_key or settings.llm_api_key
    openai_key = settings.openai_api_key or settings.llm_api_key

    # Check if automated unit tests are running
    is_test_env = (
        bool(os.getenv("PYTEST_CURRENT_TEST"))
        or settings.environment == "test"
        or os.getenv("TESTING", "").lower() in ("true", "1")
    )

    if configured == "mock":
        if not is_test_env:
            logger.warning("********************************************************************************")
            logger.warning(" LOUD WARNING: TruthGuard AI Service is running with MockLLMProvider in a       ")
            logger.warning(" NON-TEST environment (%s)! Mock responses must be explicitly supplied.     ", settings.environment)
            logger.warning(" Set GEMINI_API_KEY or OPENAI_API_KEY and LLM_PROVIDER=gemini for real inference. ")
            logger.warning("********************************************************************************")
        return "mock", settings.llm_model or "mock-grounded-model", None

    if configured == "gemini":
        return "gemini", settings.llm_model or settings.gemini_model, gemini_key

    if configured in ("openai", "openai-compatible"):
        return "openai", settings.llm_model or settings.openai_model, openai_key

    # LLM_PROVIDER is not explicitly set. Default to real provider when an API key is present:
    if gemini_key:
        return "gemini", settings.llm_model or settings.gemini_model, gemini_key
    elif openai_key:
        return "openai", settings.llm_model or settings.openai_model, openai_key

    # No API key present and LLM_PROVIDER not set:
    if is_test_env:
        return "mock", settings.llm_model or "mock-grounded-model", None

    logger.warning("********************************************************************************")
    logger.warning(" LOUD WARNING: No API key found (GEMINI_API_KEY / OPENAI_API_KEY) and LLM_PROVIDER ")
    logger.warning(" is not set. Defaulting to mock provider. To use real LLM, supply an API key.    ")
    logger.warning("********************************************************************************")
    return "mock", settings.llm_model or "mock-grounded-model", None


def get_llm_provider(
    provider_type: Optional[str] = None,
    model_name: Optional[str] = None,
    api_key: Optional[str] = None
) -> LLMProvider:
    """Factory creating configured LLM provider instance."""
    if provider_type:
        p_type = provider_type.lower().strip()
        m_name = model_name or settings.llm_model
        key = api_key or settings.llm_api_key
    else:
        p_type, m_name, key = resolve_provider_type()

    if p_type == "gemini":
        return GeminiProvider(model_name=m_name or settings.gemini_model, api_key=key)
    elif p_type in ("openai", "openai-compatible"):
        return OpenAICompatibleProvider(model_name=m_name or settings.openai_model, api_key=key)
    elif p_type == "mock":
        return MockLLMProvider(model_name=m_name or "mock-grounded-model")
    else:
        raise ValueError(f"Unsupported LLM provider '{p_type}'. Must be 'gemini', 'openai', or 'mock'.")
