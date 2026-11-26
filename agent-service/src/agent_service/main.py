"""Điểm khởi động agent-service."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Literal

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from agent_service.agent.events import AgentEvent, ErrorEvent
from agent_service.agent.loop import AgentLoop
from agent_service.config import Settings, get_settings
from agent_service.embeddings.gemini import EmbeddingError, GeminiEmbedder
from agent_service.providers.factory import build_provider
from agent_service.schemas.api import AnalyzeRequest, EmbedRequest, EmbedResponse
from agent_service.streaming import stop_on_disconnect, with_heartbeat
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
    # Hai tier, dựng cả hai lúc khởi động (cấu hình premium sai thì chết ngay, không đợi tới
    # câu hỏi đầu tiên của một dự án Premium). Premium thiếu khoá = không có khoá "premium".
    providers = {"free": build_provider(settings)}
    if settings.premium_enabled:
        providers["premium"] = build_provider(settings.premium_view())
    app.state.providers = providers
    app.state.tool_client = EvidenceToolClient(settings)
    app.state.embedder = (
        GeminiEmbedder(
            api_key=settings.effective_embedding_api_key,
            model=settings.embedding_model,
            dimensions=settings.embedding_dimensions,
            base_url=settings.embedding_base_url,
            timeout_seconds=settings.request_timeout_seconds,
            document_max_wait_seconds=settings.embedding_retry_max_wait_seconds,
            query_max_wait_seconds=settings.embedding_query_max_wait_seconds,
        )
        if settings.embedding_provider == "gemini"
        else None
    )

    logger.info(
        "agent-service khoi dong: provider=%s model=%s premium_model=%s core_api=%s prompt=%s",
        settings.llm_provider,
        settings.llm_model,
        settings.premium_llm_model if settings.premium_enabled else "(chua cau hinh)",
        settings.core_api_url,
        settings.prompt_version,
    )
    try:
        yield
    finally:
        await app.state.tool_client.aclose()
        if app.state.embedder is not None:
            await app.state.embedder.aclose()


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
    # None = tier premium chưa bật (thiếu PREMIUM_LLM_API_KEY).
    premium_model: str | None = None
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
        premium_model=settings.premium_llm_model if settings.premium_enabled else None,
        prompt_version=settings.prompt_version,
    )


def _sse(event: AgentEvent) -> str:
    """Một khung SSE. Tên sự kiện nằm ở dòng ``event:`` để phía nhận lọc mà không cần parse."""
    return f"event: {event.type.value}\ndata: {event.model_dump_json()}\n\n"


@app.post("/embed", response_model=EmbedResponse, tags=["embedding"])
async def embed(request: EmbedRequest, http_request: Request) -> EmbedResponse:
    """Nhúng văn bản thành vector — người gọi duy nhất là core-backend.

    Embedding chưa bật thì 503 kèm lý do, KHÔNG trả vector rỗng: core-backend ghi lý do đó vào lần
    phân tích để người dùng thấy vì sao tìm theo nghĩa không chạy, thay vì một tính năng câm lặng.
    """
    embedder: GeminiEmbedder | None = http_request.app.state.embedder
    if embedder is None:
        raise HTTPException(
            status_code=503,
            detail="Embedding chua bat: dat EMBEDDING_PROVIDER=gemini cho agent-service.",
        )
    try:
        vectors = await embedder.embed(request.texts, request.task)
    except EmbeddingError as exc:
        # 502: lỗi nằm ở nhà cung cấp phía sau, không phải ở yêu cầu của core-backend.
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return EmbedResponse(model=embedder.model, dimensions=embedder.dimensions, vectors=vectors)


@app.post("/analyze", tags=["agent"])
async def analyze(request: AnalyzeRequest, http_request: Request) -> StreamingResponse:
    """Chạy một lượt hỏi đáp và stream tiến trình.

    Trả về SSE chứ không phải một JSON duy nhất vì một lượt suy luận đi qua nhiều vòng gọi tool
    và có thể kéo dài hàng chục giây. Cái được stream là tiến trình truy xuất bằng chứng, KHÔNG
    phải từng token của câu trả lời — xem Javadoc của ``agent/events.py``.

    Không có CORS ở đây: người gọi là core-backend, server-to-server.
    """
    state = http_request.app.state
    provider = state.providers.get(request.model_tier)

    async def stream() -> AsyncIterator[str]:
        if provider is None:
            # Không rơi về model free: người dùng đã chọn Sonnet thì câu trả lời mà thực ra do
            # Gemini sinh ra là sai lệch số liệu, tệ hơn một thông báo lỗi.
            yield _sse(
                ErrorEvent(
                    message="Model Premium chua duoc cau hinh tren may chu "
                    "(thieu PREMIUM_LLM_API_KEY). Hay hoi bang model Free."
                )
            )
            return
        loop = AgentLoop(provider, state.tool_client, state.settings)
        async for event in loop.run(
            analysis_run_id=str(request.analysis_run_id),
            question=request.question,
            focus_short_id=request.focus_short_id,
            history=[turn.model_dump() for turn in request.history],
        ):
            yield _sse(event)

    # Bọc bằng stop_on_disconnect: core-backend đóng kết nối khi người dùng bấm "Dừng" hoặc rời
    # trang, và lượt gọi LLM đang bay phải bị huỷ NGAY — không đợi tới khung SSE kế tiếp.
    # with_heartbeat chen ": ping" khi im lặng: core-backend cắt lượt theo thời gian IM LẶNG.
    return StreamingResponse(
        stop_on_disconnect(with_heartbeat(stream()), http_request.is_disconnected),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            # Nginx mặc định gom response lại rồi xả một cục, làm streaming đứng im rồi nhảy
            # một phát (kế hoạch §1 điểm 5). Header này tắt hành vi đó.
            "X-Accel-Buffering": "no",
        },
    )
