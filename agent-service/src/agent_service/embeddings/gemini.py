"""Gọi API embedding của Gemini (bản gốc, không qua lớp tương thích OpenAI).

Vì sao không dùng endpoint tương thích OpenAI như phần chat: chỉ API gốc nhận ``taskType``
(``RETRIEVAL_DOCUMENT`` cho bằng chứng, ``RETRIEVAL_QUERY`` cho câu hỏi). Gemini huấn luyện hai
chế độ đó khác nhau, và nhúng câu hỏi bằng chế độ tài liệu làm giảm chất lượng tìm kiếm — đúng
thứ tính năng này sinh ra để cải thiện.

Khoá API đi trong header ``x-goog-api-key``, KHÔNG trong query string: URL có khoá sẽ nằm nguyên
văn trong log của mọi proxy trên đường đi.

**Chờ hạn mức rồi gửi lại** (sửa 26/11/2026). Bản free giới hạn 100 đoạn văn bản/phút, và Google
đếm TỪNG đoạn trong một lần ``batchEmbedContents`` là một yêu cầu. Run vulnshop có 124 bằng chứng:
lô 1 (100 đoạn) ăn hết hạn mức của phút đó, lô 2 gửi ngay nên nhận 429 — mà trước đây gặp 429 là
bỏ cuộc, nên mọi run trên 100 bằng chứng KHÔNG BAO GIỜ nhúng được. Nay gặp lỗi tạm thời thì chờ
đúng thời gian Google yêu cầu rồi gửi lại chính lô đó, trong một ngân sách chờ có giới hạn.
"""

from __future__ import annotations

import asyncio
import logging
import re
from dataclasses import dataclass
from typing import Any, Literal

import httpx

logger = logging.getLogger(__name__)

EmbeddingTask = Literal["document", "query"]

_TASK_TYPES: dict[EmbeddingTask, str] = {
    "document": "RETRIEVAL_DOCUMENT",
    "query": "RETRIEVAL_QUERY",
}

# Giới hạn của batchEmbedContents: tối đa 100 yêu cầu mỗi lần gọi.
BATCH_SIZE = 100

# Gián tiếp qua biến module để test thay được, khỏi phải ngủ thật (cùng mẫu với agent/loop.py).
_sleep = asyncio.sleep

# Chờ thêm chừng này sau thời gian Google yêu cầu: hạn mức tính theo cửa sổ phút, gửi lại đúng
# mốc thì dễ rơi vào cuối cửa sổ cũ và ăn thêm một lần 429.
_WAIT_MARGIN_SECONDS = 1.0
# Lỗi tạm thời mà Google không nói phải chờ bao lâu (5xx, mất kết nối): lùi dần 5, 10, 20… giây.
_FALLBACK_BACKOFF_SECONDS = 5.0
_MAX_BACKOFF_SECONDS = 60.0

_RETRY_IN = re.compile(r"retry in\s+([0-9]+(?:\.[0-9]+)?)\s*s", re.IGNORECASE)


class EmbeddingError(Exception):
    """Gọi API embedding thất bại — thông điệp đọc được, không chứa khoá."""


@dataclass
class _WaitBudget:
    """Tổng thời gian được phép chờ cho MỘT lời gọi ``embed`` (dùng chung mọi lô của nó)."""

    total: float
    used: float = 0.0

    @property
    def remaining(self) -> float:
        return self.total - self.used


class GeminiEmbedder:
    """Nhúng văn bản thành vector bằng ``models/<model>:batchEmbedContents``."""

    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        dimensions: int,
        base_url: str,
        timeout_seconds: float,
        document_max_wait_seconds: float = 150.0,
        query_max_wait_seconds: float = 10.0,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._api_key = api_key
        self._model = model
        self._dimensions = dimensions
        self._url = f"{base_url.rstrip('/')}/models/{model}:batchEmbedContents"
        self._client = client or httpx.AsyncClient(timeout=timeout_seconds)
        # Câu hỏi (query) chờ ngắn: nó nằm giữa một lượt gọi tool của Agent, và hỏng thì
        # search_evidence vẫn còn so khớp chữ. Bằng chứng (document) chờ được lâu hơn: sinh một lần
        # cho cả run, hỏng là run mất hẳn tìm theo nghĩa.
        self._max_wait: dict[EmbeddingTask, float] = {
            "document": document_max_wait_seconds,
            "query": query_max_wait_seconds,
        }

    @property
    def model(self) -> str:
        return self._model

    @property
    def dimensions(self) -> int:
        return self._dimensions

    async def embed(self, texts: list[str], task: EmbeddingTask) -> list[list[float]]:
        """Trả đúng ``len(texts)`` vector, cùng thứ tự với đầu vào."""
        budget = _WaitBudget(self._max_wait[task])
        vectors: list[list[float]] = []
        for start in range(0, len(texts), BATCH_SIZE):
            vectors.extend(
                await self._embed_batch(texts[start : start + BATCH_SIZE], task, budget, start)
            )
        return vectors

    async def _embed_batch(
        self, texts: list[str], task: EmbeddingTask, budget: _WaitBudget, offset: int
    ) -> list[list[float]]:
        body = {
            "requests": [
                {
                    "model": f"models/{self._model}",
                    "content": {"parts": [{"text": text}]},
                    "taskType": _TASK_TYPES[task],
                    "outputDimensionality": self._dimensions,
                }
                for text in texts
            ]
        }
        failures = 0
        while True:
            requested: float | None = None
            try:
                response = await self._client.post(
                    self._url, json=body, headers={"x-goog-api-key": self._api_key}
                )
            except httpx.TransportError as exc:
                message = f"khong goi duoc Gemini embedding: {exc}"
            except httpx.HTTPError as exc:
                raise EmbeddingError(f"khong goi duoc Gemini embedding: {exc}") from exc
            else:
                if response.status_code < 400:
                    return self._parse(response, texts)
                message = (
                    f"Gemini embedding tra ve HTTP {response.status_code}: "
                    f"{_error_detail(response)}"
                )
                if response.status_code != 429 and response.status_code < 500:
                    raise EmbeddingError(message)
                if _is_daily_quota(response):
                    # Chờ vài phút cũng vô ích — hạn mức chỉ hồi lại vào ngày hôm sau.
                    raise EmbeddingError(f"{message}; het han muc trong ngay, thu lai vao ngay mai")
                requested = _requested_delay(response)

            failures += 1
            backoff = min(_FALLBACK_BACKOFF_SECONDS * 2 ** (failures - 1), _MAX_BACKOFF_SECONDS)
            wait = (requested if requested is not None else backoff) + _WAIT_MARGIN_SECONDS
            if wait > budget.remaining:
                # Dừng NGAY thay vì ngủ hết phần còn lại rồi vẫn hỏng.
                raise EmbeddingError(
                    f"{message}; da cho {budget.used:.0f} giay, het ngan sach cho "
                    f"{budget.total:.0f} giay (can cho them {wait:.0f} giay)"
                )
            logger.info(
                "Gemini embedding loi tam thoi o lo bat dau tu doan %s (%s), cho %.1fs roi gui lai",
                offset,
                message,
                wait,
            )
            await _sleep(wait)
            budget.used += wait

    def _parse(self, response: httpx.Response, texts: list[str]) -> list[list[float]]:
        embeddings = response.json().get("embeddings")
        if not isinstance(embeddings, list) or len(embeddings) != len(texts):
            # Thiếu một vector là lệch thứ tự cả lô — vector của bằng chứng A gắn vào bằng chứng B.
            raise EmbeddingError(
                f"Gemini tra ve {len(embeddings) if isinstance(embeddings, list) else 0} vector "
                f"cho {len(texts)} doan van ban"
            )
        vectors = [list(map(float, item["values"])) for item in embeddings]
        for vector in vectors:
            if len(vector) != self._dimensions:
                raise EmbeddingError(
                    f"Gemini tra ve vector {len(vector)} chieu, cau hinh la {self._dimensions}"
                )
        return vectors

    async def aclose(self) -> None:
        await self._client.aclose()


def _error_object(response: httpx.Response) -> dict[str, Any] | None:
    """Object ``error`` trong thân lỗi của Google (thân có thể là object hoặc mảng object)."""
    try:
        payload: Any = response.json()
    except ValueError:
        return None
    if isinstance(payload, list) and payload:
        payload = payload[0]
    error = payload.get("error") if isinstance(payload, dict) else None
    return error if isinstance(error, dict) else None


def _error_detail(response: httpx.Response) -> str:
    """Rút câu giải thích từ thân lỗi của Google."""
    error = _error_object(response)
    if error is not None:
        return f"{error.get('status', '')}: {error.get('message', '')}".strip(": ")
    try:
        return str(response.json())[:300]
    except ValueError:
        return response.text[:300]


def _requested_delay(response: httpx.Response) -> float | None:
    """Số giây Google yêu cầu chờ, theo thứ tự tin cậy: ``RetryInfo`` → câu trong message → header.

    Không có ở đâu cả thì ``None`` (người gọi lùi dần).
    """
    error = _error_object(response)
    if error is not None:
        for detail in error.get("details") or []:
            if isinstance(detail, dict) and str(detail.get("@type", "")).endswith(
                "google.rpc.RetryInfo"
            ):
                seconds = _duration_seconds(detail.get("retryDelay"))
                if seconds is not None:
                    return seconds
        match = _RETRY_IN.search(str(error.get("message", "")))
        if match:
            return float(match.group(1))
    try:
        header = response.headers.get("retry-after")
        return float(header) if header is not None else None
    except ValueError:
        return None


def _duration_seconds(value: object) -> float | None:
    """``google.protobuf.Duration`` dạng JSON (``"6s"``, ``"6.44s"``) sang số giây."""
    if isinstance(value, str) and value.endswith("s"):
        try:
            return float(value[:-1])
        except ValueError:
            return None
    return None


def _is_daily_quota(response: httpx.Response) -> bool:
    """429 vì hết hạn mức THEO NGÀY (``quotaId`` dạng ``...PerDay...``) chứ không phải theo phút."""
    return response.status_code == 429 and "PerDay" in response.text
