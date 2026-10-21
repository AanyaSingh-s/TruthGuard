"""TruthGuard AI Service Main Application Entrypoint."""

import logging
import sys
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.health import router as health_router
from app.api.extraction import router as extraction_router
from app.config import settings

# Configure structured logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s]: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("truthguard-ai")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Initializing TruthGuard AI Service (v%s)...", settings.app_version)
    logger.info("Environment: %s", settings.environment)
    yield
    logger.info("TruthGuard AI Service shut down gracefully.")


app = FastAPI(
    title="TruthGuard AI Service",
    description="Vector retrieval, claim extraction, and adversarial inspection engine for TruthGuard.",
    version=settings.app_version,
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc"
)

# Enable CORS for frontend and orchestrator backend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error("Unhandled error processing request %s: %s", request.url.path, str(exc), exc_info=True)
    return JSONResponse(
        status_code=500,
        content={
            "error": "InternalServerError",
            "message": "An unexpected error occurred in TruthGuard AI Service.",
            "path": request.url.path
        }
    )


# Include routers
app.include_router(health_router)
app.include_router(extraction_router)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host=settings.host, port=settings.port, reload=True)
