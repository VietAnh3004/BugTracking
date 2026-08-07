"""Cổng ra Evidence Tool API của core-backend.

Đây là ĐƯỜNG DUY NHẤT agent-service lấy được bằng chứng — không có kết nối cơ sở dữ liệu nào
(ADR 0001). Một tool call của LLM tương ứng đúng một POST ở đây, quan hệ 1:1 tuyệt đối, nên
số liệu "agent đã truy xuất bao nhiêu lần, mất bao lâu" đo được mà không cần suy đoán.
"""

import logging
import time
from types import TracebackType
from typing import Any, Self

import httpx

from agent_service.config import Settings
from agent_service.tools.schemas import TOOL_NAMES

logger = logging.getLogger(__name__)


class EvidenceToolError(RuntimeError):
    """core-backend từ chối hoặc không trả lời được một tool call."""


class ToolCallOutcome:
    """Kết quả một lần gọi tool, kèm số đo để ghi vào ``llm_trace``."""

    __slots__ = ("arguments", "duration_ms", "error", "item_count", "name", "result")

    def __init__(
        self,
        name: str,
        arguments: dict[str, Any],
        result: dict[str, Any] | None,
        duration_ms: int,
        item_count: int | None,
        error: str | None,
    ) -> None:
        self.name = name
        self.arguments = arguments
        self.result = result
        self.duration_ms = duration_ms
        self.item_count = item_count
        self.error = error

    def as_trace_entry(self) -> dict[str, Any]:
        """Dạng đưa vào ``llm_trace.tool_calls`` — KHÔNG kèm payload, chỉ số đo."""
        return {
            "name": self.name,
            "arguments": self.arguments,
            "duration_ms": self.duration_ms,
            "item_count": self.item_count,
            "error": self.error,
        }

    def as_tool_result_text(self) -> str:
        """Nội dung trả lại cho model."""
        if self.error is not None:
            return f"LỖI khi gọi {self.name}: {self.error}"
        import json

        return json.dumps(self.result, ensure_ascii=False)


class EvidenceToolClient:
    """Gọi ``/api/v1/tools/<tên tool>`` bằng header ``X-Service-Token``."""

    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None) -> None:
        self._base_url = str(settings.core_api_url).rstrip("/")
        self._owns_client = client is None
        self._client = client or httpx.AsyncClient(
            timeout=httpx.Timeout(settings.request_timeout_seconds),
            headers={"X-Service-Token": settings.service_token},
        )

    async def __aenter__(self) -> Self:
        return self

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        tb: TracebackType | None,
    ) -> None:
        await self.aclose()

    async def aclose(self) -> None:
        if self._owns_client:
            await self._client.aclose()

    async def call(
        self, name: str, arguments: dict[str, Any], analysis_run_id: str
    ) -> ToolCallOutcome:
        """Gọi một tool và trả về kết quả kèm số đo.

        ``analysis_run_id`` do agent loop điền, KHÔNG lấy từ ``arguments``: phạm vi tra cứu bằng
        chứng không phải thứ LLM được chọn. Giá trị trùng tên trong ``arguments`` bị ghi đè.

        Lỗi mạng và lỗi HTTP KHÔNG ném ra ngoài mà thành một ``ToolCallOutcome`` mang ``error``:
        model cần đọc được thông báo lỗi để đổi cách hỏi, và một tool hỏng không được phép giết
        cả lượt suy luận. Tên tool sai thì khác — đó là bug của chính ta, phải nổ.
        """
        if name not in TOOL_NAMES:
            raise EvidenceToolError(f"Tool khong ton tai: {name}")

        payload = {**arguments, "analysis_run_id": analysis_run_id}
        started = time.monotonic()
        try:
            response = await self._client.post(
                f"{self._base_url}/api/v1/tools/{name}", json=payload
            )
            response.raise_for_status()
            result = response.json()
        except httpx.HTTPStatusError as exc:
            duration_ms = int((time.monotonic() - started) * 1000)
            detail = _problem_detail(exc.response)
            logger.warning(
                "tool_call name=%s status=%s detail=%s", name, exc.response.status_code, detail
            )
            return ToolCallOutcome(name, payload, None, duration_ms, None, detail)
        except httpx.HTTPError as exc:
            duration_ms = int((time.monotonic() - started) * 1000)
            logger.warning("tool_call name=%s loi_mang=%s", name, exc)
            return ToolCallOutcome(
                name, payload, None, duration_ms, None, f"khong goi duoc core-backend: {exc}"
            )

        duration_ms = int((time.monotonic() - started) * 1000)
        item_count = _item_count(result)
        logger.info("tool_call name=%s duration_ms=%s item_count=%s", name, duration_ms, item_count)
        return ToolCallOutcome(name, payload, result, duration_ms, item_count, None)


def _item_count(result: dict[str, Any]) -> int | None:
    """Số bằng chứng trả về, hoặc ``None`` khi tool không trả danh sách (``get_warning``)."""
    total = result.get("total")
    return total if isinstance(total, int) else None


def _problem_detail(response: httpx.Response) -> str:
    """Lấy phần người đọc được của ProblemDetail; lùi về status khi thân không phải JSON."""
    try:
        body = response.json()
    except ValueError:
        return f"HTTP {response.status_code}"
    if isinstance(body, dict):
        detail = body.get("detail") or body.get("title")
        if isinstance(detail, str):
            return f"HTTP {response.status_code}: {detail}"
    return f"HTTP {response.status_code}"
