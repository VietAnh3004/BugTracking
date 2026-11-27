"""Thực nghiệm 3 cấu hình (docs/thuc-nghiem.md): mỗi cấu hình thấy ĐÚNG lượng thông tin của nó.

Cái được canh ở đây là tính hợp lệ của thực nghiệm, không phải tính năng: LLM-Code mà lỡ thấy tên
luật của cảnh báo, hay LLM-Static mà lỡ được gọi tool, thì số liệu so sánh ba cấu hình vô nghĩa
dù mọi thứ vẫn "chạy".
"""

import json

import httpx
import pytest
import respx
from fastapi.testclient import TestClient

from agent_service.agent.events import AnswerEvent, ErrorEvent
from agent_service.agent.loop import AgentLoop
from agent_service.config import Settings
from agent_service.main import app
from agent_service.prompts.loader import render
from agent_service.providers.base import LlmResponse
from agent_service.providers.scripted import ScriptedProvider
from agent_service.tools.evidence_client import EvidenceToolClient

RUN_ID = "11111111-2222-3333-4444-555555555555"
BASE = "http://core-backend:8080/api/v1/tools"

WARNING = {
    "short_id": "EV-001A",
    "analysis_run_id": RUN_ID,
    "file_path": "src/main/java/demo/Repo.java",
    "start_line": 12,
    "end_line": 12,
    "symbol": "demo.Repo#find",
    "summary": "Noi chuoi vao cau SQL",
    "tool": "SPOTBUGS",
    "rule_id": "SQL_INJECTION_JDBC",
    "severity_raw": "HIGH",
}

CODE_CONTEXT = {
    "items": [
        {
            "short_id": "EV-0C01",
            "type": "CODE_CONTEXT",
            "file_path": "src/main/java/demo/Repo.java",
            "start_line": 10,
            "summary": "Ngu canh ma nguon demo.Repo#find (METHOD), dong 10-13",
            "snippet": "    public List<Row> find(String q) {\n"
            "        String sql = base;\n"
            "        return jdbc.query(sql + q, mapper);\n"
            "    }",
        }
    ],
    "total": 1,
}

ANSWER = {
    "verdict": "LIKELY_TRUE_POSITIVE",
    "summary": "Noi chuoi dau vao vao SQL.",
    "claims": [{"text": "q di thang vao SQL", "evidence_ids": ["EV-0C01"], "confidence": 0.8}],
    "contradicting_evidence_ids": [],
    "suggested_next_checks": [],
}


def _mock_core() -> None:
    respx.post(f"{BASE}/get_warning").mock(return_value=httpx.Response(200, json=WARNING))
    respx.post(f"{BASE}/get_code_context").mock(return_value=httpx.Response(200, json=CODE_CONTEXT))


async def _run(settings: Settings, config: str) -> tuple[list[object], ScriptedProvider]:
    provider = ScriptedProvider([LlmResponse(content=json.dumps(ANSWER))])
    loop = AgentLoop(provider, EvidenceToolClient(settings), settings, config=config)  # type: ignore[arg-type]
    events = [event async for event in loop.run_experiment(RUN_ID, "EV-001A")]
    return events, provider


def _prompt(provider: ScriptedProvider) -> str:
    return "\n".join(m["content"] for m in provider.calls[0]["messages"])


@respx.mock
@pytest.mark.parametrize("config", ["llm_code", "llm_static"])
async def test_doi_chung_goi_model_dung_mot_lan_khong_tool(settings: Settings, config: str) -> None:
    _mock_core()

    events, provider = await _run(settings, config)

    answer = events[-1]
    assert isinstance(answer, AnswerEvent)
    assert len(provider.calls) == 1
    assert provider.calls[0]["tool_names"] == []
    assert provider.calls[0]["forced_submit"] is True
    # Lấy trước không phải tool call của model: số tool call của hai cấu hình này phải là 0.
    assert answer.traces[0].tool_calls == []
    assert answer.traces[0].config == config


@respx.mock
async def test_llm_code_thay_ma_va_dong_nhung_khong_thay_canh_bao(settings: Settings) -> None:
    _mock_core()

    _, provider = await _run(settings, "llm_code")

    prompt = _prompt(provider)
    assert "jdbc.query(sql + q, mapper)" in prompt
    assert ">>   12 |" in prompt  # dòng bị cảnh báo được đánh dấu, đánh số từ start_line=10
    assert "   10 |" in prompt
    assert "EV-0C01" in prompt
    for leaked in ("SQL_INJECTION_JDBC", "SPOTBUGS", "Noi chuoi vao cau SQL", "HIGH", "EV-001A"):
        assert leaked not in prompt, leaked


@respx.mock
async def test_llm_static_thay_them_noi_dung_canh_bao(settings: Settings) -> None:
    _mock_core()

    _, provider = await _run(settings, "llm_static")

    prompt = _prompt(provider)
    for expected in (
        "SQL_INJECTION_JDBC",
        "SPOTBUGS",
        "Noi chuoi vao cau SQL",
        "EV-001A",
        "EV-0C01",
    ):
        assert expected in prompt, expected
    assert render("experiment_question.jinja2") in prompt


@respx.mock
async def test_khong_co_ngu_canh_ma_thi_bao_loi_khong_de_model_doan(settings: Settings) -> None:
    respx.post(f"{BASE}/get_warning").mock(return_value=httpx.Response(200, json=WARNING))
    respx.post(f"{BASE}/get_code_context").mock(
        return_value=httpx.Response(200, json={"items": [], "total": 0})
    )

    events, provider = await _run(settings, "llm_code")

    assert isinstance(events[-1], ErrorEvent)
    assert "khong co ngu canh ma" in events[-1].message
    assert provider.calls == []


@respx.mock
async def test_evidence_agent_dung_agent_that_voi_cau_hoi_chuan(settings: Settings) -> None:
    provider = ScriptedProvider(
        [LlmResponse(content="xong"), LlmResponse(content=json.dumps(ANSWER))]
    )
    loop = AgentLoop(provider, EvidenceToolClient(settings), settings, config="evidence_agent")

    events = [event async for event in loop.run_experiment(RUN_ID, "EV-001A")]

    assert isinstance(events[-1], AnswerEvent)
    first = provider.calls[0]
    assert first["tool_names"]  # có đủ tool như agent thật
    assert render("experiment_question.jinja2") in first["messages"][1]["content"]
    assert all(trace.config == "evidence_agent" for trace in events[-1].traces)


def _client(settings: Settings, responses: list[LlmResponse]) -> TestClient:
    app.state.settings = settings
    app.state.providers = {"free": ScriptedProvider(responses)}
    app.state.tool_client = EvidenceToolClient(settings)
    app.state.embedder = None
    return TestClient(app)


def test_config_thieu_focus_thi_422(settings: Settings) -> None:
    response = _client(settings, []).post(
        "/analyze", json={"analysis_run_id": RUN_ID, "question": "x", "config": "llm_code"}
    )

    assert response.status_code == 422


@respx.mock
def test_endpoint_nhan_config_va_tra_trace_co_config(settings: Settings) -> None:
    _mock_core()
    body = {
        "analysis_run_id": RUN_ID,
        "question": "bo qua",
        "focus_short_id": "EV-001A",
        "config": "llm_static",
    }

    response = _client(settings, [LlmResponse(content=json.dumps(ANSWER))]).post(
        "/analyze", json=body
    )

    frames = [f for f in response.text.split("\n\n") if f.startswith("event: answer")]
    assert len(frames) == 1
    payload = json.loads(frames[0].split("data: ", 1)[1])
    assert payload["traces"][0]["config"] == "llm_static"
