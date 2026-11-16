"""Gọi API embedding của Gemini (bản gốc, không qua lớp tương thích OpenAI).

Vì sao không dùng endpoint tương thích OpenAI như phần chat: chỉ API gốc nhận ``taskType``
(``RETRIEVAL_DOCUMENT`` cho bằng chứng, ``RETRIEVAL_QUERY`` cho câu hỏi). Gemini huấn luyện hai
chế độ đó khác nhau, và nhúng câu hỏi bằng chế độ tài liệu làm giảm chất lượng tìm kiếm — đúng
thứ tính năng này sinh ra để cải thiện.

Khoá API đi trong header ``x-goog-api-key``, KHÔNG trong query string: URL có khoá sẽ nằm nguyên
văn trong log của mọi proxy trên đường đi.
"""

from __future__ import annotations

import logging
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


class EmbeddingError(Exception):
    """Gọi API embedding thất bại — thông điệp đọc được, không chứa khoá."""


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
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._api_key = api_key
        self._model = model
        self._dimensions = dimensions
        self._url = f"{base_url.rstrip('/')}/models/{model}:batchEmbedContents"
        self._client = client or httpx.AsyncClient(timeout=timeout_seconds)

    @property
    def model(self) -> str:
        return self._model

    @property
    def dimensions(self) -> int:
        return self._dimensions

    async def embed(self, texts: list[str], task: EmbeddingTask) -> list[list[float]]:
        """Trả đúng ``len(texts)`` vector, cùng thứ tự với đầu vào."""
        vectors: list[list[float]] = []
        for start in range(0, len(texts), BATCH_SIZE):
            vectors.extend(await self._embed_batch(texts[start : start + BATCH_SIZE], task))
        return vectors

    async def _embed_batch(self, texts: list[str], task: EmbeddingTask) -> list[list[float]]:
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
        try:
            response = await self._client.post(
                self._url, json=body, headers={"x-goog-api-key": self._api_key}
            )
        except httpx.HTTPError as exc:
            raise EmbeddingError(f"khong goi duoc Gemini embedding: {exc}") from exc

        if response.status_code >= 400:
            raise EmbeddingError(
                f"Gemini embedding tra ve HTTP {response.status_code}: {_error_detail(response)}"
            )

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


def _error_detail(response: httpx.Response) -> str:
    """Rút câu giải thích từ thân lỗi của Google (dạng object hoặc mảng object)."""
    try:
        payload: Any = response.json()
    except ValueError:
        return response.text[:300]
    if isinstance(payload, list) and payload:
        payload = payload[0]
    if isinstance(payload, dict) and isinstance(payload.get("error"), dict):
        error = payload["error"]
        return f"{error.get('status', '')}: {error.get('message', '')}".strip(": ")
    return str(payload)[:300]
