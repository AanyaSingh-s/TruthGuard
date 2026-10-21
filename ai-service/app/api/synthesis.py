"""TruthGuard Answer Synthesizer Agent API endpoints."""

import logging
from fastapi import APIRouter, HTTPException, status

from app.api.retrieval import retriever_service
from app.schemas.retrieval import RetrievalRequest
from app.schemas.synthesis import SynthesisRequest, SynthesisResponse
from app.services.llm import (
    LLMProviderError,
    LLMTimeoutError,
    MalformedLLMResponseError,
)
from app.services.synthesizer import SynthesizerService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1", tags=["Answer Synthesis"])

# Singleton synthesizer service instance
synthesizer_service = SynthesizerService()


@router.post(
    "/synthesize",
    response_model=SynthesisResponse,
    summary="Synthesize grounded candidate answer with verified claim citations",
    description="Synthesizes candidate answer from retrieved evidence passages, breaks it down into atomic claims, and validates citations."
)
async def synthesize_candidate_answer(request: SynthesisRequest) -> SynthesisResponse:
    try:
        # If passages are omitted, automatically invoke the Retriever Agent
        passages = request.passages
        if passages is None:
            retrieval_res = retriever_service.retrieve(RetrievalRequest(
                question=request.question,
                filters=request.filters,
                top_k=request.top_k or 5
            ))
            passages = retrieval_res.passages

        synth_req = SynthesisRequest(
            question=request.question,
            passages=passages,
            temperature=request.temperature,
            max_tokens=request.max_tokens
        )
        return synthesizer_service.synthesize(synth_req)

    except ValueError as e:
        logger.warning("Invalid synthesis request: %s", e)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": "InvalidRequestError", "message": str(e)}
        )
    except MalformedLLMResponseError as e:
        logger.error("Malformed LLM JSON output: %s", e)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail={"error": "MalformedLLMResponseError", "message": str(e)}
        )
    except LLMTimeoutError as e:
        logger.error("LLM provider timeout: %s", e)
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail={"error": "LLMTimeoutError", "message": str(e)}
        )
    except LLMProviderError as e:
        logger.error("LLM provider API failure: %s", e)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail={"error": "LLMProviderError", "message": str(e)}
        )
    except Exception as e:
        logger.error("Unexpected synthesis server error: %s", e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"error": "SynthesisServerError", "message": f"Server failed during synthesis: {str(e)}"}
        )
