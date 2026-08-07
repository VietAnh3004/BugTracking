"""Vòng lặp agent: truy xuất bằng chứng rồi nộp câu trả lời có cấu trúc.

Không test nào gọi LLM thật (quy ước của repo) — mọi lượt của model đến từ
:class:`ScriptedProvider`, nên kịch bản lặp lại được y hệt qua các lần chạy.
"""

import json

import httpx
import pytest
import respx
from tests.conftest import items_result

from agent_service.agent.events import (
    AnswerEvent,
    ErrorEvent,
    EventType,
    ToolCallEvent,
    ToolResultEvent,
)
from agent_service.agent.loop import AgentLoop
from agent_service.config import Settings
from agent_service.providers.base import LlmResponse, ToolCall
from agent_service.providers.scripted import ScriptedProvider
from agent_service.tools.evidence_client import EvidenceToolClient

RUN_ID = "11111111-2222-3333-4444-555555555555"
BASE = "http://core-backend:8080/api/v1/tools"

ANSWER = {
    "verdict": "LIKELY_TRUE_POSITIVE",
    "summary": "Dong 66 giai tham chieu mot gia tri co the null.",
    "claims": [
        {
            "text": "findFirst tra ve null khi khong tim thay",
            "evidence_ids": ["EV-001A"],
            "confidence": 0.9,
        }
    ],
    "contradicting_evidence_ids": [],
    "suggested_next_checks": [],
}


def _submit(answer: dict[str, object] | None = None) -> LlmResponse:
    return LlmResponse(content=json.dumps(answer if answer is not None else ANSWER))


def _wants_tool(name: str, **arguments: object) -> LlmResponse:
    return LlmResponse(
        content=None, tool_calls=[ToolCall(id=f"toolu_{name}", name=name, arguments=arguments)]
    )


async def _collect(loop: AgentLoop, question: str = "Canh bao nay co that khong?") -> list[object]:
    return [event async for event in loop.run(RUN_ID, question, focus_short_id="EV-001A")]


def _build(settings: Settings, responses: list[LlmResponse]) -> tuple[AgentLoop, ScriptedProvider]:
    provider = ScriptedProvider(responses)
    return AgentLoop(provider, EvidenceToolClient(settings), settings), provider


@respx.mock
async def test_goi_tool_roi_nop_cau_tra_loi(settings: Settings) -> None:
    respx.post(f"{BASE}/get_warning").mock(
        return_value=httpx.Response(200, json={"short_id": "EV-001A"})
    )
    loop, _ = _build(
        settings,
        [_wants_tool("get_warning", evidence_id="EV-001A"), LlmResponse(content="xong"), _submit()],
    )

    events = await _collect(loop)

    assert next(e for e in events if isinstance(e, ToolCallEvent)).name == "get_warning"
    assert next(e for e in events if isinstance(e, ToolResultEvent)).error is None
    answer = events[-1]
    assert isinstance(answer, AnswerEvent)
    assert answer.answer.verdict == "LIKELY_TRUE_POSITIVE"


@respx.mock
async def test_khong_goi_tool_nao_van_nop_duoc(settings: Settings) -> None:
    """Model trả lời thẳng thì vẫn phải đi qua bước nộp có cấu trúc."""
    loop, _ = _build(settings, [LlmResponse(content="Toi khong can tra cuu gi."), _submit()])

    events = await _collect(loop)

    assert not [e for e in events if isinstance(e, ToolCallEvent)]
    assert isinstance(events[-1], AnswerEvent)


@respx.mock
async def test_nhieu_tool_trong_mot_luot(settings: Settings) -> None:
    """Model gọi song song nhiều tool: mỗi cái phải có đủ một cặp sự kiện gọi/kết quả."""
    respx.post(f"{BASE}/get_warning").mock(
        return_value=httpx.Response(200, json={"short_id": "EV-001A"})
    )
    respx.post(f"{BASE}/get_coverage").mock(return_value=httpx.Response(200, json=items_result(2)))

    song_song = LlmResponse(
        content=None,
        tool_calls=[
            ToolCall(id="t1", name="get_warning", arguments={"evidence_id": "EV-001A"}),
            ToolCall(id="t2", name="get_coverage", arguments={"evidence_id": "EV-001A"}),
        ],
    )
    loop, _ = _build(settings, [song_song, LlmResponse(content="du roi"), _submit()])

    events = await _collect(loop)

    assert [e.name for e in events if isinstance(e, ToolCallEvent)] == [
        "get_warning",
        "get_coverage",
    ]
    assert len([e for e in events if isinstance(e, ToolResultEvent)]) == 2


@respx.mock
async def test_het_ngan_sach_vong_lap_van_nop_cau_tra_loi(settings: Settings) -> None:
    """Model gọi tool mãi không thôi thì phải dừng có kiểm soát, không treo và không im lặng cắt.

    ``MAX_TOOL_ITERATIONS=3`` trong fixture, nên đúng ba vòng truy xuất rồi tới lượt nộp.
    """
    respx.post(f"{BASE}/get_coverage").mock(return_value=httpx.Response(200, json=items_result(0)))
    xin_them = _wants_tool("get_coverage", evidence_id="EV-001A")
    loop, provider = _build(settings, [xin_them, xin_them, xin_them, _submit()])

    events = await _collect(loop)

    assert len([e for e in events if isinstance(e, ToolCallEvent)]) == 3
    assert isinstance(events[-1], AnswerEvent)
    # Lượt cuối phải mang lời nhắc hết ngân sách, nếu không model không biết vì sao bị dừng.
    cuoi = provider.calls[-1]["messages"][-1]
    assert "hết số vòng" in cuoi["content"]


@respx.mock
async def test_tool_hong_khong_giet_ca_luot(settings: Settings) -> None:
    """core-backend trả 404 cho một Evidence ID bịa: agent vẫn phải kết luận được."""
    respx.post(f"{BASE}/get_warning").mock(
        return_value=httpx.Response(
            404, json={"title": "evidence-not-found", "detail": "khong thay"}
        )
    )
    loop, _ = _build(
        settings,
        [_wants_tool("get_warning", evidence_id="EV-FFFF"), LlmResponse(content="ok"), _submit()],
    )

    events = await _collect(loop)

    ket_qua = next(e for e in events if isinstance(e, ToolResultEvent))
    assert ket_qua.error is not None
    assert isinstance(events[-1], AnswerEvent)


@respx.mock
async def test_phan_hoi_sai_luoc_do_thanh_loi_khong_thanh_verdict_tu_che(
    settings: Settings,
) -> None:
    """Không được chữa cháy bằng một câu trả lời rỗng.

    Một verdict tự chế sẽ đi thẳng vào số liệu tuần 15 mà không ai phân biệt được nó với một
    kết luận thật của mô hình.
    """
    loop, _ = _build(
        settings, [LlmResponse(content="xong"), LlmResponse(content='{"verdict": "KHONG_CO_THAT"}')]
    )

    events = await _collect(loop)

    assert isinstance(events[-1], ErrorEvent)
    assert events[-1].traces[-1].error_message is not None


@respx.mock
async def test_trace_ghi_du_moi_vong_ke_ca_vong_hong(settings: Settings) -> None:
    """``llm_trace`` là nguồn cho chương 4 báo cáo.

    Thiếu một vòng là mất số liệu không tái tạo lại được.
    """
    respx.post(f"{BASE}/get_coverage").mock(return_value=httpx.Response(200, json=items_result(1)))
    loop, _ = _build(
        settings,
        [_wants_tool("get_coverage", evidence_id="EV-001A"), LlmResponse(content="ok"), _submit()],
    )

    events = await _collect(loop)

    answer = events[-1]
    assert isinstance(answer, AnswerEvent)
    assert [t.iteration for t in answer.traces] == [0, 1, 2]
    assert answer.traces[0].tool_calls[0]["name"] == "get_coverage"
    assert all(t.prompt_version == "v1" for t in answer.traces)


@respx.mock
async def test_ket_qua_tool_dai_bi_cat_trong_trace(settings: Settings) -> None:
    """Giữ nguyên xi thì bảng trace phình nhanh hơn mọi bảng khác, mà nội dung vẫn tra lại được."""
    respx.post(f"{BASE}/get_code_context").mock(
        return_value=httpx.Response(200, json=items_result(400))
    )
    loop, _ = _build(
        settings,
        [
            _wants_tool("get_code_context", evidence_id="EV-001A"),
            LlmResponse(content="ok"),
            _submit(),
        ],
    )

    events = await _collect(loop)
    answer = events[-1]
    assert isinstance(answer, AnswerEvent)

    ban_ghi_tool = next(m for m in answer.traces[-1].request_messages if m["role"] == "tool")
    assert "cat bot" in ban_ghi_tool["content"]
    assert len(ban_ghi_tool["content"]) < 2200


@respx.mock
async def test_luot_nop_moi_ep_luoc_do(settings: Settings) -> None:
    """Ép định dạng và cho phép gọi tool loại trừ nhau.

    Bật ép nộp từ vòng đầu nghĩa là buộc model gọi đúng một tool, nên nó sẽ không bao giờ truy
    xuất được bằng chứng nào — kết luận lúc đó là suy đoán thuần tuý.
    """
    respx.post(f"{BASE}/get_warning").mock(
        return_value=httpx.Response(200, json={"short_id": "EV-001A"})
    )
    loop, provider = _build(
        settings,
        [_wants_tool("get_warning", evidence_id="EV-001A"), LlmResponse(content="ok"), _submit()],
    )

    await _collect(loop)

    assert [call["forced_submit"] for call in provider.calls] == [False, False, True]
    assert provider.calls[0]["tool_names"] == [
        "get_warning",
        "get_code_context",
        "get_related_tests",
        "get_coverage",
        "search_evidence",
    ]


@respx.mock
async def test_su_kien_dau_tien_la_status(settings: Settings) -> None:
    """Người dùng phải thấy phản hồi trước khi vòng gọi model đầu tiên xong."""
    loop, _ = _build(settings, [LlmResponse(content="ok"), _submit()])

    events = await _collect(loop)

    assert events[0].type == EventType.STATUS


@pytest.mark.parametrize("thieu", ["verdict", "summary"])
@respx.mock
async def test_thieu_truong_bat_buoc_thi_thanh_loi(settings: Settings, thieu: str) -> None:
    khiem_khuyet = {k: v for k, v in ANSWER.items() if k != thieu}
    loop, _ = _build(settings, [LlmResponse(content="ok"), _submit(khiem_khuyet)])

    events = await _collect(loop)

    assert isinstance(events[-1], ErrorEvent)


@respx.mock
async def test_provider_scripted_chay_that_khong_het_kich_ban(settings: Settings) -> None:
    """``LLM_PROVIDER=scripted`` phải đi hết một lượt hỏi đáp.

    Một lượt gọi model ÍT NHẤT hai lần (vòng truy xuất, rồi lượt nộp có ép lược đồ), nên kịch bản
    mặc định một phản hồi sẽ chết ở lượt thứ hai — và chết đúng ở đường mà tài liệu bảo dùng để
    nghiệm thu end-to-end. Lỗi này chỉ lộ ra khi chạy thật, không test nào của agent loop cũ đi
    qua nhánh `from_settings`.
    """
    provider = ScriptedProvider.from_settings(settings)
    loop = AgentLoop(provider, EvidenceToolClient(settings), settings)

    events = [event async for event in loop.run(RUN_ID, "Cau hoi bat ky")]

    assert isinstance(events[-1], AnswerEvent)
    assert events[-1].answer.verdict == "INSUFFICIENT_EVIDENCE"
    assert len(provider.calls) == 2
