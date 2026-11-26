"""Embedding Gemini: đúng request (taskType, số chiều, khoá trong header), đúng thứ tự vector,
và lỗi đọc được. Không gọi API thật — respx chặn mọi request HTTP.
"""

import json

import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from agent_service.config import Settings
from agent_service.embeddings import gemini as gemini_module
from agent_service.embeddings.gemini import BATCH_SIZE, EmbeddingError, GeminiEmbedder
from agent_service.main import app
from agent_service.providers.scripted import ScriptedProvider
from agent_service.tools.evidence_client import EvidenceToolClient

URL = "https://gemini.test/v1beta/models/gemini-embedding-001:batchEmbedContents"
PER_MINUTE = "EmbedContentRequestsPerMinutePerProjectPerModel-FreeTier"


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
async def test_loi_cua_google_doc_duoc_va_loi_cau_hinh_khong_thu_lai(
    no_sleep: list[float],
) -> None:
    route = respx.post(URL).mock(
        return_value=httpx.Response(
            400,
            json=[{"error": {"status": "INVALID_ARGUMENT", "message": "Bad dimensions"}}],
        )
    )

    with pytest.raises(EmbeddingError, match="400: INVALID_ARGUMENT: Bad dimensions"):
        await _embedder().embed(["a"], "document")

    assert route.call_count == 1
    assert no_sleep == []


# ---- Chờ hạn mức rồi gửi lại (viec-con-mo.md mục 1 cũ, sửa 26/11/2026) -----------------------


@pytest.fixture
def no_sleep(monkeypatch: pytest.MonkeyPatch) -> list[float]:
    """Thay việc chờ bằng ghi lại số giây — test không ngủ thật."""
    delays: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        delays.append(seconds)

    monkeypatch.setattr(gemini_module, "_sleep", fake_sleep)
    return delays


def _quota_exceeded(
    *, retry_delay: str | None = None, message: str = "Quota exceeded", quota_id: str = PER_MINUTE
) -> httpx.Response:
    """429 đúng hình dạng Google trả cho bản free: mảng một object, kèm QuotaFailure + RetryInfo."""
    details: list[dict[str, object]] = [
        {
            "@type": "type.googleapis.com/google.rpc.QuotaFailure",
            "violations": [{"quotaId": quota_id}],
        }
    ]
    if retry_delay is not None:
        details.append(
            {"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": retry_delay}
        )
    return httpx.Response(
        429,
        json=[
            {
                "error": {
                    "code": 429,
                    "status": "RESOURCE_EXHAUSTED",
                    "message": message,
                    "details": details,
                }
            }
        ],
    )


@respx.mock
async def test_lo_thu_hai_bi_429_thi_cho_dung_retry_delay_roi_gui_lai_chinh_lo_do(
    no_sleep: list[float],
) -> None:
    """Đúng ca gặp thật: run 124 bằng chứng, lô 1 ăn hết 100 đoạn/phút, lô 2 nhận 429."""
    sizes: list[int] = []
    replies = iter([None, _quota_exceeded(retry_delay="7s"), None])

    def reply(request: httpx.Request) -> httpx.Response:
        count = len(json.loads(request.content)["requests"])
        sizes.append(count)
        return next(replies) or _reply(count)

    respx.post(URL).mock(side_effect=reply)

    vectors = await _embedder().embed([f"t{i}" for i in range(BATCH_SIZE + 24)], "document")

    assert len(vectors) == BATCH_SIZE + 24
    assert vectors[BATCH_SIZE] == [0.0, 0.0, 0.0]  # lô 2 bắt đầu lại từ vector 0 của nó
    assert sizes == [BATCH_SIZE, 24, 24]
    assert no_sleep == [pytest.approx(8.0)]  # 7 giây Google yêu cầu + 1 giây đệm


@respx.mock
async def test_khong_co_retry_info_thi_doc_cau_please_retry_in(no_sleep: list[float]) -> None:
    respx.post(URL).mock(
        side_effect=[
            _quota_exceeded(message="Quota exceeded ... Please retry in 6.44s."),
            _reply(1),
        ]
    )

    await _embedder().embed(["a"], "document")

    assert no_sleep == [pytest.approx(7.44)]


@respx.mock
async def test_429_mai_thi_dung_khi_het_ngan_sach_cho(no_sleep: list[float]) -> None:
    respx.post(URL).mock(return_value=_quota_exceeded(retry_delay="40s"))

    with pytest.raises(EmbeddingError, match=r"429: RESOURCE_EXHAUSTED.*het ngan sach cho 150"):
        await _embedder().embed(["a"], "document")

    assert no_sleep == [41.0, 41.0, 41.0]  # lần thứ tư cần thêm 41 > 27 giây còn lại
    assert sum(no_sleep) <= 150


@respx.mock
async def test_retry_delay_dai_hon_ngan_sach_thi_bao_loi_ngay_khong_ngu_vo_ich(
    no_sleep: list[float],
) -> None:
    respx.post(URL).mock(return_value=_quota_exceeded(retry_delay="3600s"))

    with pytest.raises(EmbeddingError, match="can cho them 3601 giay"):
        await _embedder().embed(["a"], "document")

    assert no_sleep == []


@respx.mock
async def test_het_han_muc_theo_ngay_thi_khong_cho(no_sleep: list[float]) -> None:
    respx.post(URL).mock(
        return_value=_quota_exceeded(
            retry_delay="5s", quota_id="EmbedContentRequestsPerDayPerProjectPerModel-FreeTier"
        )
    )

    with pytest.raises(EmbeddingError, match="het han muc trong ngay"):
        await _embedder().embed(["a"], "document")

    assert no_sleep == []


@respx.mock
async def test_cau_hoi_chi_cho_ngan_vi_con_so_khop_chu(no_sleep: list[float]) -> None:
    """Nhúng câu hỏi nằm giữa một lượt gọi tool của Agent: chờ 30 giây là Agent đứng hình."""
    respx.post(URL).mock(return_value=_quota_exceeded(retry_delay="30s"))

    with pytest.raises(EmbeddingError, match="het ngan sach cho 10"):
        await _embedder().embed(["test nao hong?"], "query")

    assert no_sleep == []


@respx.mock
async def test_loi_5xx_va_mat_ket_noi_thi_lui_dan_roi_thu_lai(no_sleep: list[float]) -> None:
    respx.post(URL).mock(
        side_effect=[
            httpx.Response(503, json={"error": {"status": "UNAVAILABLE", "message": "overloaded"}}),
            httpx.ConnectError("connection reset"),
            _reply(1),
        ]
    )

    vectors = await _embedder().embed(["a"], "document")

    assert vectors == [[0.0, 0.0, 0.0]]
    assert no_sleep == [6.0, 11.0]  # lùi dần 5, 10 giây + 1 giây đệm


def _client(settings: Settings, embedder: GeminiEmbedder | None) -> TestClient:
    app.state.settings = settings
    app.state.providers = {"free": ScriptedProvider([])}
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
