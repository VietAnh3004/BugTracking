"""Endpoint ``POST /analyze`` — hợp đồng SSE với core-backend.

Người gọi duy nhất là core-backend, nên khung SSE ở đây là một hợp đồng thật giữa hai service:
sai tên sự kiện thì phía nhận lọc trượt và câu trả lời không bao giờ tới người dùng.
"""

import json

import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from agent_service.agent.events import EventType
from agent_service.config import Settings
from agent_service.main import app
from agent_service.providers.base import LlmResponse, ToolCall
from agent_service.providers.scripted import ScriptedProvider
from agent_service.tools.evidence_client import EvidenceToolClient

RUN_ID = "11111111-2222-3333-4444-555555555555"
BASE = "http://core-backend:8080/api/v1/tools"

ANSWER = {
    "verdict": "LIKELY_FALSE_POSITIVE",
    "summary": "Dau vao da duoc lam sach o tang tren.",
    "claims": [{"text": "Controller da validate", "evidence_ids": ["EV-001A"], "confidence": 0.7}],
    "contradicting_evidence_ids": ["EV-002B"],
    "suggested_next_checks": ["Kiem tra them cac loi vao khac"],
}


@pytest.fixture
def client(settings: Settings) -> TestClient:
    """Nạp app với provider giả, bỏ qua lifespan thật (nó sẽ đi dựng provider Anthropic)."""
    app.state.settings = settings
    app.state.provider = ScriptedProvider(
        [
            LlmResponse(
                content=None,
                tool_calls=[
                    ToolCall(id="t1", name="get_warning", arguments={"evidence_id": "EV-001A"})
                ],
            ),
            LlmResponse(content="da du bang chung"),
            LlmResponse(content=json.dumps(ANSWER)),
        ]
    )
    app.state.tool_client = EvidenceToolClient(settings)
    return TestClient(app)


def _frames(body: str) -> list[tuple[str, dict[str, object]]]:
    frames = []
    for block in body.strip().split("\n\n"):
        lines = block.splitlines()
        name = lines[0].removeprefix("event: ")
        payload = json.loads(lines[1].removeprefix("data: "))
        frames.append((name, payload))
    return frames


@respx.mock
def test_stream_du_cac_loai_su_kien(client: TestClient) -> None:
    respx.post(f"{BASE}/get_warning").mock(
        return_value=httpx.Response(200, json={"short_id": "EV-001A"})
    )

    response = client.post(
        "/analyze",
        json={
            "analysis_run_id": RUN_ID,
            "question": "Canh bao nay co that khong?",
            "focus_short_id": "EV-001A",
        },
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    names = [name for name, _ in _frames(response.text)]
    assert names[0] == EventType.STATUS.value
    assert EventType.TOOL_CALL.value in names
    assert EventType.TOOL_RESULT.value in names
    assert names[-1] == EventType.ANSWER.value


@respx.mock
def test_su_kien_answer_mang_ca_cau_tra_loi_va_trace(client: TestClient) -> None:
    """core-backend cần cả hai trong MỘT sự kiện.

    Nó ghi ``message``, ``claim`` và ``llm_trace`` trong cùng một transaction.
    """
    respx.post(f"{BASE}/get_warning").mock(
        return_value=httpx.Response(200, json={"short_id": "EV-001A"})
    )

    response = client.post("/analyze", json={"analysis_run_id": RUN_ID, "question": "hoi"})

    _, payload = _frames(response.text)[-1]
    assert payload["answer"]["verdict"] == "LIKELY_FALSE_POSITIVE"
    assert payload["answer"]["contradicting_evidence_ids"] == ["EV-002B"]
    assert len(payload["traces"]) == 3
    assert payload["traces"][0]["tool_calls"][0]["name"] == "get_warning"


@respx.mock
def test_header_tat_buffering_cua_nginx(client: TestClient) -> None:
    """Thiếu header này thì Nginx gom cả stream lại rồi xả một cục (kế hoạch §1 điểm 5)."""
    respx.post(f"{BASE}/get_warning").mock(return_value=httpx.Response(200, json={}))

    response = client.post("/analyze", json={"analysis_run_id": RUN_ID, "question": "hoi"})

    assert response.headers["X-Accel-Buffering"] == "no"


def test_thieu_analysis_run_id_thi_422(client: TestClient) -> None:
    """Phạm vi tra cứu bằng chứng là bắt buộc: không có nó thì Evidence ID vô nghĩa."""
    assert client.post("/analyze", json={"question": "hoi"}).status_code == 422


def test_cau_hoi_rong_thi_422(client: TestClient) -> None:
    assert (
        client.post("/analyze", json={"analysis_run_id": RUN_ID, "question": ""}).status_code == 422
    )


def test_health_bao_ca_phien_ban_prompt(client: TestClient) -> None:
    """Không biết prompt nào đang chạy thì kết quả thực nghiệm không tái lập được."""
    body = client.get("/health").json()

    assert body["status"] == "ok"
    assert body["prompt_version"] == "v1"
