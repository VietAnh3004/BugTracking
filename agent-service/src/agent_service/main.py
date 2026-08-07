"""Điểm khởi động agent-service."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Literal

from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from agent_service.agent.events import AgentEvent
from agent_service.agent.loop import AgentLoop
from agent_service.config import Settings, get_settings
from agent_service.providers.factory import build_provider
from agent_service.schemas.api import AnalyzeRequest
from agent_service.tools.evidence_client import EvidenceToolClient

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Dựng provider và client HTTP một lần, lúc khởi động.

    Provider dựng ở đây chứ không dựng theo từng request là có chủ đích: cấu hình sai (thiếu
    khoá, tham số model không nhận) phải làm container chết ngay lúc deploy, chứ không phải hỏng
    giữa một phiên hội thoại của người dùng.
    """
    settings = get_settings()
    logging.basicConfig(level=settings.log_level)

    app.state.settings = settings
    app.state.provider = build_provider(settings)
    app.state.tool_client = EvidenceToolClient(settings)

    logger.info(
        "agent-service khoi dong: provider=%s model=%s core_api=%s prompt=%s",
        settings.llm_provider,
        settings.llm_model,
        settings.core_api_url,
        settings.prompt_version,
    )
    try:
        yield
    finally:
        await app.state.tool_client.aclose()


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
    prompt_version: str


@app.get("/health", response_model=HealthResponse, tags=["system"])
async def health(http_request: Request) -> HealthResponse:
    """Báo service còn sống và đang cấu hình với provider nào.

    Đọc từ ``app.state`` chứ không gọi lại :func:`get_settings`: cái cần báo là cấu hình mà tiến
    trình ĐANG chạy, không phải cấu hình đọc lại từ môi trường lúc bị hỏi.
    """
    settings: Settings = http_request.app.state.settings
    return HealthResponse(
        status="ok",
        provider=settings.llm_provider,
        model=settings.llm_model,
        prompt_version=settings.prompt_version,
    )


def _sse(event: AgentEvent) -> str:
    """Một khung SSE. Tên sự kiện nằm ở dòng ``event:`` để phía nhận lọc mà không cần parse."""
    return f"event: {event.type.value}\ndata: {event.model_dump_json()}\n\n"


@app.post("/analyze", tags=["agent"])
async def analyze(request: AnalyzeRequest, http_request: Request) -> StreamingResponse:
    """Chạy một lượt hỏi đáp và stream tiến trình.

    Trả về SSE chứ không phải một JSON duy nhất vì một lượt suy luận đi qua nhiều vòng gọi tool
    và có thể kéo dài hàng chục giây. Cái được stream là tiến trình truy xuất bằng chứng, KHÔNG
    phải từng token của câu trả lời — xem Javadoc của ``agent/events.py``.

    Không có CORS ở đây: người gọi là core-backend, server-to-server.
    """
    state = http_request.app.state
    loop = AgentLoop(state.provider, state.tool_client, state.settings)

    async def stream() -> AsyncIterator[str]:
        async for event in loop.run(
            analysis_run_id=str(request.analysis_run_id),
            question=request.question,
            focus_short_id=request.focus_short_id,
            history=[turn.model_dump() for turn in request.history],
        ):
            yield _sse(event)

    return StreamingResponse(
        stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            # Nginx mặc định gom response lại rồi xả một cục, làm streaming đứng im rồi nhảy
            # một phát (kế hoạch §1 điểm 5). Header này tắt hành vi đó.
            "X-Accel-Buffering": "no",
        },
    )
