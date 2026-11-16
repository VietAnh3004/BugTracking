"""Embedding Gemini: đúng request (taskType, số chiều, khoá trong header), đúng thứ tự vector,
và lỗi đọc được. Không gọi API thật — respx chặn mọi request HTTP.
"""

import json

import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from agent_service.config import Settings
from agent_service.embeddings.gemini import BATCH_SIZE, EmbeddingError, GeminiEmbedder
from agent_service.main import app
from agent_service.providers.scripted import ScriptedProvider
from agent_service.tools.evidence_client import EvidenceToolClient

URL = "https://gemini.test/v1beta/models/gemini-embedding-001:batchEmbedContents"


def _embedder(dimensions: int = 3) -> GeminiEmbedder:
    return GeminiEmbedder(
        api_key="khoa-bi-mat",
        model="gemini-embedding-001",
        dimensions=dimensions,
        base_url="https://gemini.test/v1beta",
        timeout_seconds=5,
    )


def _reply(count: int, dimensions: int = 3) -> httpx.Response:
    return httpx.Response(
        200,
        json={"embeddings": [{"values": [float(i)] * dimensions} for i in range(count)]},
    )


@respx.mock
async def test_gui_dung_task_type_so_chieu_va_khoa_trong_header() -> None:
    route = respx.post(URL).mock(return_value=_reply(2))

    vectors = await _embedder().embed(["a", "b"], "query")

    assert vectors == [[0.0, 0.0, 0.0], [1.0, 1.0, 1.0]]
    request = route.calls.last.request
    # Khoá ở header, KHÔNG ở URL — URL nằm nguyên văn trong log của proxy.
    assert request.headers["x-goog-api-key"] == "khoa-bi-mat"
    assert "khoa-bi-mat" not in str(request.url)
    body = json.loads(request.content)
    assert {r["taskType"] for r in body["requests"]} == {"RETRIEVAL_QUERY"}
    assert {r["outputDimensionality"] for r in body["requests"]} == {3}


@respx.mock
async def test_chia_lo_theo_gioi_han_100_va_giu_dung_thu_tu() -> None:
    sizes: list[int] = []

    def reply(request: httpx.Request) -> httpx.Response:
        count = len(json.loads(request.content)["requests"])
        sizes.append(count)
        return _reply(count)

    respx.post(URL).mock(side_effect=reply)

    vectors = await _embedder().embed([f"t{i}" for i in range(BATCH_SIZE + 5)], "document")

    assert sizes == [BATCH_SIZE, 5]
    assert len(vectors) == BATCH_SIZE + 5


@respx.mock
async def test_thieu_vector_la_loi_khong_lech_thu_tu_im_lang() -> None:
    respx.post(URL).mock(return_value=_reply(1))

    with pytest.raises(EmbeddingError, match="1 vector cho 2"):
        await _embedder().embed(["a", "b"], "document")


@respx.mock
async def test_loi_cua_google_doc_duoc() -> None:
    respx.post(URL).mock(
        return_value=httpx.Response(
            429,
            json={"error": {"status": "RESOURCE_EXHAUSTED", "message": "Quota exceeded"}},
        )
    )

    with pytest.raises(EmbeddingError, match="429: RESOURCE_EXHAUSTED: Quota exceeded"):
        await _embedder().embed(["a"], "document")


def _client(settings: Settings, embedder: GeminiEmbedder | None) -> TestClient:
    app.state.settings = settings
    app.state.provider = ScriptedProvider([])
    app.state.tool_client = EvidenceToolClient(settings)
    app.state.embedder = embedder
    return TestClient(app)


def test_embedding_chua_bat_thi_503_kem_ly_do(settings: Settings) -> None:
    response = _client(settings, None).post("/embed", json={"texts": ["a"], "task": "query"})

    assert response.status_code == 503
    assert "EMBEDDING_PROVIDER" in response.json()["detail"]


@respx.mock
def test_endpoint_tra_model_va_vector(settings: Settings) -> None:
    respx.post(URL).mock(return_value=_reply(1))

    response = _client(settings, _embedder()).post(
        "/embed", json={"texts": ["mat khau bam MD5"], "task": "document"}
    )

    assert response.status_code == 200
    assert response.json() == {
        "model": "gemini-embedding-001",
        "dimensions": 3,
        "vectors": [[0.0, 0.0, 0.0]],
    }
