"""Điểm khởi động agent-service."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Literal

from fastapi import FastAPI
from pydantic import BaseModel

from agent_service.config import Settings, get_settings

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Nạp cấu hình lúc khởi động để lỗi thiếu biến môi trường lộ ra ngay."""
    settings = get_settings()
    logging.basicConfig(level=settings.log_level)
    logger.info(
        "agent-service khởi động: provider=%s model=%s core_api=%s",
        settings.llm_provider,
        settings.llm_model,
        settings.core_api_url,
    )
    yield


app = FastAPI(
    title="Evidence-Grounded AI Agent",
    version="0.1.0",
    lifespan=lifespan,
)


class HealthResponse(BaseModel):
    """Trạng thái service, dùng cho healthcheck của container."""

    status: Literal["ok"]
    provider: str
    model: str


@app.get("/health", response_model=HealthResponse, tags=["system"])
async def health() -> HealthResponse:
    """Báo service còn sống và đang cấu hình với provider nào."""
    settings: Settings = get_settings()
    return HealthResponse(
        status="ok",
        provider=settings.llm_provider,
        model=settings.llm_model,
    )
