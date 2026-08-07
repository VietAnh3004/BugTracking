"""Cổng ra Evidence Tool API — ranh giới kiến trúc duy nhất để lấy bằng chứng.

Mọi test dùng ``respx`` để giả HTTP; không có test nào chạm vào core-backend thật.
"""

import httpx
import pytest
import respx
from tests.conftest import items_result

from agent_service.config import Settings
from agent_service.tools.evidence_client import EvidenceToolClient, EvidenceToolError

RUN_ID = "11111111-2222-3333-4444-555555555555"
BASE = "http://core-backend:8080/api/v1/tools"


@pytest.fixture
def client(settings: Settings) -> EvidenceToolClient:
    return EvidenceToolClient(settings)


@respx.mock
async def test_gui_header_service_token(client: EvidenceToolClient) -> None:
    """Không có header này thì core-backend trả 401 — và JWT người dùng không thay được."""
    route = respx.post(f"{BASE}/get_coverage").mock(
        return_value=httpx.Response(200, json=items_result(1))
    )

    await client.call("get_coverage", {"evidence_id": "EV-01A2"}, RUN_ID)

    assert route.calls.last.request.headers["X-Service-Token"] == "bi-mat-chia-se"


@respx.mock
async def test_analysis_run_id_do_agent_loop_dien(client: EvidenceToolClient) -> None:
    """Phạm vi tra cứu bằng chứng KHÔNG phải thứ LLM được chọn.

    Đây là điều kiện để Evidence ID có nghĩa: short_id chỉ duy nhất trong một run. Nếu model
    tự điền được, nó có thể (vô tình hoặc không) trỏ sang run khác và mọi trích dẫn hết kiểm
    chứng được.
    """
    route = respx.post(f"{BASE}/get_warning").mock(
        return_value=httpx.Response(200, json={"short_id": "EV-01A2"})
    )

    await client.call(
        "get_warning", {"evidence_id": "EV-01A2", "analysis_run_id": "run-do-llm-bia"}, RUN_ID
    )

    import json

    body = json.loads(route.calls.last.request.content)
    assert body["analysis_run_id"] == RUN_ID


@respx.mock
async def test_dem_so_muc_tra_ve(client: EvidenceToolClient) -> None:
    respx.post(f"{BASE}/get_related_tests").mock(
        return_value=httpx.Response(200, json=items_result(4))
    )

    outcome = await client.call("get_related_tests", {"evidence_id": "EV-01A2"}, RUN_ID)

    assert outcome.item_count == 4
    assert outcome.error is None


@respx.mock
async def test_get_warning_khong_co_total_thi_item_count_la_none(
    client: EvidenceToolClient,
) -> None:
    """``get_warning`` trả một object, không phải danh sách — đếm 0 sẽ nói dối."""
    respx.post(f"{BASE}/get_warning").mock(
        return_value=httpx.Response(200, json={"short_id": "EV-01A2"})
    )

    outcome = await client.call("get_warning", {"evidence_id": "EV-01A2"}, RUN_ID)

    assert outcome.item_count is None


@respx.mock
async def test_loi_http_thanh_ket_qua_doc_duoc_khong_nem(client: EvidenceToolClient) -> None:
    """Một tool hỏng không được giết cả lượt suy luận.

    Model cần đọc được thông báo lỗi để đổi cách hỏi (vd hỏi sai Evidence ID thì thử tìm lại
    bằng search_evidence). Ném ra ngoài sẽ biến một lỗi phục hồi được thành một lượt hỏng.
    """
    respx.post(f"{BASE}/get_warning").mock(
        return_value=httpx.Response(
            404, json={"title": "evidence-not-found", "detail": "Khong thay EV-ZZZZ"}
        )
    )

    outcome = await client.call("get_warning", {"evidence_id": "EV-FFFF"}, RUN_ID)

    assert outcome.error is not None
    assert "Khong thay EV-ZZZZ" in outcome.error
    assert "LỖI" in outcome.as_tool_result_text()


@respx.mock
async def test_loi_mang_cung_thanh_ket_qua_doc_duoc(client: EvidenceToolClient) -> None:
    respx.post(f"{BASE}/get_coverage").mock(side_effect=httpx.ConnectError("core-backend chet"))

    outcome = await client.call("get_coverage", {"evidence_id": "EV-01A2"}, RUN_ID)

    assert outcome.error is not None
    assert outcome.result is None


async def test_ten_tool_khong_ton_tai_thi_nem(client: EvidenceToolClient) -> None:
    """Khác hẳn lỗi HTTP: tên tool sai là bug của chính ta, phải nổ chứ không được nuốt."""
    with pytest.raises(EvidenceToolError):
        await client.call("get_everything", {}, RUN_ID)


@respx.mock
async def test_trace_entry_khong_kem_payload(client: EvidenceToolClient) -> None:
    """``llm_trace.tool_calls`` giữ SỐ ĐO, không giữ nội dung bằng chứng.

    Giữ cả payload thì bảng trace phình nhanh hơn mọi bảng khác — mà nội dung đó vẫn tra lại
    được từ Evidence Store bất cứ lúc nào.
    """
    respx.post(f"{BASE}/get_related_tests").mock(
        return_value=httpx.Response(200, json=items_result(2))
    )

    outcome = await client.call("get_related_tests", {"evidence_id": "EV-01A2"}, RUN_ID)
    entry = outcome.as_trace_entry()

    assert set(entry) == {"name", "arguments", "duration_ms", "item_count", "error"}
    assert entry["item_count"] == 2
