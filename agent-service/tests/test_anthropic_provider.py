"""Ánh xạ hội thoại sang định dạng Messages API của Anthropic.

Không gọi API thật: một client giả ghi lại đúng payload đã dựng. Cái đáng test ở đây không phải
"có gọi được không" mà là **hình dạng payload** — mọi lỗi ở đây đều thuộc loại chỉ lộ ra khi
chạy thật với khoá thật, tức là muộn nhất có thể.
"""

from types import SimpleNamespace
from typing import Any

import pytest

from agent_service.config import Settings
from agent_service.providers.anthropic_provider import AnthropicProvider
from agent_service.providers.base import Message, ToolCall


class FakeMessages:
    def __init__(self, response: Any) -> None:
        self._response = response
        self.request: dict[str, Any] = {}

    async def create(self, **kwargs: Any) -> Any:
        self.request = kwargs
        return self._response


class FakeClient:
    def __init__(self, response: Any) -> None:
        self.messages = FakeMessages(response)


def _block(**kwargs: Any) -> SimpleNamespace:
    return SimpleNamespace(**kwargs)


def _response(content: list[SimpleNamespace], stop_reason: str = "end_turn") -> SimpleNamespace:
    return SimpleNamespace(
        content=content,
        stop_reason=stop_reason,
        usage=SimpleNamespace(input_tokens=120, output_tokens=45),
    )


def _provider(
    settings: Settings, response: SimpleNamespace
) -> tuple[AnthropicProvider, FakeClient]:
    anthropic_settings = settings.model_copy(
        update={"llm_provider": "anthropic", "llm_model": "claude-sonnet-5"}
    )
    client = FakeClient(response)
    return AnthropicProvider(anthropic_settings, client=client), client


def test_temperature_lam_provider_nem_ngay_luc_khoi_dong(settings: Settings) -> None:
    """Các model từ Claude Opus 4.7 trở đi trả 400 cho tham số này.

    Nổ lúc dựng provider (trong lifespan) thay vì để mọi request hỏng giữa hội thoại — cùng
    nguyên tắc fail-fast mà core-backend áp cho biến môi trường thiếu.
    """
    voi_temperature = settings.model_copy(
        update={"llm_provider": "anthropic", "llm_temperature": 0.0}
    )

    with pytest.raises(ValueError, match="LLM_TEMPERATURE"):
        AnthropicProvider(voi_temperature, client=FakeClient(_response([])))


async def test_system_tach_ra_khoi_messages(settings: Settings) -> None:
    """Anthropic nhận system như một tham số riêng, không phải một lượt trong ``messages``."""
    provider, client = _provider(settings, _response([_block(type="text", text="xin chao")]))

    await provider.chat(
        [Message(role="system", content="luat choi"), Message(role="user", content="hoi")]
    )

    assert client.messages.request["system"] == "luat choi"
    assert [m["role"] for m in client.messages.request["messages"]] == ["user"]


async def test_ket_qua_tool_lien_ke_gop_thanh_mot_luot(settings: Settings) -> None:
    """Đặc tả đòi mọi ``tool_result`` của cùng một lượt nằm trong MỘT message người dùng.

    Tách ra nhiều message khiến model dần thôi gọi tool song song — hỏng âm thầm, chỉ lộ ra ở
    số vòng lặp tăng dần và chi phí tăng theo, không có thông báo lỗi nào.
    """
    provider, client = _provider(settings, _response([_block(type="text", text="xong")]))

    await provider.chat(
        [
            Message(role="user", content="hoi"),
            Message(
                role="assistant",
                content="",
                tool_calls=[
                    ToolCall(id="t1", name="get_warning", arguments={}),
                    ToolCall(id="t2", name="get_coverage", arguments={}),
                ],
            ),
            Message(role="tool", content="ket qua 1", tool_call_id="t1", name="get_warning"),
            Message(role="tool", content="ket qua 2", tool_call_id="t2", name="get_coverage"),
        ]
    )

    turns = client.messages.request["messages"]
    assert [t["role"] for t in turns] == ["user", "assistant", "user"]
    assert len(turns[2]["content"]) == 2
    assert [b["tool_use_id"] for b in turns[2]["content"]] == ["t1", "t2"]


async def test_luot_assistant_giu_lai_tool_use(settings: Settings) -> None:
    """Bỏ khối ``tool_use`` đi thì lượt ``tool_result`` phía sau không còn cặp — API từ chối."""
    provider, client = _provider(settings, _response([_block(type="text", text="xong")]))

    await provider.chat(
        [
            Message(role="user", content="hoi"),
            Message(
                role="assistant",
                content="de toi tra",
                tool_calls=[
                    ToolCall(id="t1", name="get_warning", arguments={"evidence_id": "EV-001A"})
                ],
            ),
            Message(role="tool", content="{}", tool_call_id="t1", name="get_warning"),
        ]
    )

    blocks = client.messages.request["messages"][1]["content"]
    assert [b["type"] for b in blocks] == ["text", "tool_use"]
    assert blocks[1]["input"] == {"evidence_id": "EV-001A"}


async def test_luot_assistant_rong_van_co_noi_dung(settings: Settings) -> None:
    """Message rỗng bị API từ chối; lỗi phải đọc được thay vì là 400 từ phía hãng."""
    provider, client = _provider(settings, _response([_block(type="text", text="xong")]))

    await provider.chat(
        [Message(role="user", content="hoi"), Message(role="assistant", content="")]
    )

    assert client.messages.request["messages"][1]["content"]


async def test_khong_gui_tham_so_sampling(settings: Settings) -> None:
    provider, client = _provider(settings, _response([_block(type="text", text="xong")]))

    await provider.chat([Message(role="user", content="hoi")])

    assert not {"temperature", "top_p", "top_k"} & set(client.messages.request)


async def test_ep_nop_thi_ket_qua_vao_content_khong_vao_tool_calls(settings: Settings) -> None:
    """JSON của ``submit_analysis`` CHÍNH LÀ câu trả lời cuối.

    Để nó rơi vào ``tool_calls`` sẽ khiến agent loop đi thêm một vòng vô nghĩa rồi hết ngân sách.
    """
    submit = _block(
        type="tool_use", id="t9", name="submit_analysis", input={"verdict": "INSUFFICIENT_EVIDENCE"}
    )
    provider, client = _provider(settings, _response([submit], stop_reason="tool_use"))

    result = await provider.chat(
        [Message(role="user", content="hoi")], response_schema={"type": "object"}
    )

    assert result.tool_calls == []
    assert result.content is not None
    assert "INSUFFICIENT_EVIDENCE" in result.content
    assert client.messages.request["tool_choice"] == {"type": "tool", "name": "submit_analysis"}


async def test_tool_thuong_van_vao_tool_calls(settings: Settings) -> None:
    goi = _block(type="tool_use", id="t1", name="get_warning", input={"evidence_id": "EV-001A"})
    provider, _ = _provider(settings, _response([goi], stop_reason="tool_use"))

    result = await provider.chat(
        [Message(role="user", content="hoi")], tools=[{"name": "get_warning"}]
    )

    assert [c.name for c in result.tool_calls] == ["get_warning"]
    assert result.content is None


async def test_so_token_di_vao_ket_qua(settings: Settings) -> None:
    """Không đọc được số token thì ``llm_trace`` mất cột đắt nhất của chương 4."""
    provider, _ = _provider(settings, _response([_block(type="text", text="xong")]))

    result = await provider.chat([Message(role="user", content="hoi")])

    assert (result.input_tokens, result.output_tokens) == (120, 45)
    assert result.stop_reason == "end_turn"
