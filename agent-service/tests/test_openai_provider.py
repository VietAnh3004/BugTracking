"""Ánh xạ hội thoại sang Chat Completions của API tương thích OpenAI (Gemini, Groq, Ollama…).

Không gọi API thật: client giả ghi lại đúng payload đã dựng, cùng lý do với
``test_anthropic_provider.py`` — mọi lỗi hình dạng payload chỉ lộ ra khi chạy với khoá thật.
"""

import json
from types import SimpleNamespace
from typing import Any

from agent_service.config import Settings
from agent_service.providers.base import SUBMIT_TOOL_NAME, Message, ToolCall
from agent_service.providers.openai_provider import OpenAICompatibleProvider, inline_refs
from agent_service.schemas.analysis import AnalysisResponse
from agent_service.tools.schemas import ALL_TOOLS

GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/"


class FakeToolCall:
    """Giả lập đối tượng pydantic của SDK, kể cả trường lạ mà SDK giữ lại như ``extra``."""

    def __init__(self, data: dict[str, Any]) -> None:
        self._data = data

    def model_dump(self, exclude_none: bool = False) -> dict[str, Any]:
        return dict(self._data)


class FakeCompletions:
    def __init__(self, response: Any) -> None:
        self._response = response
        self.request: dict[str, Any] = {}

    async def create(self, **kwargs: Any) -> Any:
        self.request = kwargs
        return self._response


class FakeClient:
    def __init__(self, response: Any) -> None:
        self.chat = SimpleNamespace(completions=FakeCompletions(response))


def _response(
    content: str | None = "xong",
    tool_calls: list[FakeToolCall] | None = None,
    finish_reason: str = "stop",
) -> SimpleNamespace:
    return SimpleNamespace(
        choices=[
            SimpleNamespace(
                message=SimpleNamespace(content=content, tool_calls=tool_calls),
                finish_reason=finish_reason,
            )
        ],
        usage=SimpleNamespace(prompt_tokens=200, completion_tokens=30),
    )


def _provider(
    settings: Settings, response: SimpleNamespace, **overrides: Any
) -> tuple[OpenAICompatibleProvider, FakeClient]:
    openai_settings = settings.model_copy(
        update={
            "llm_provider": "openai",
            "llm_model": "gemini-3.8-flash",
            "llm_base_url": GEMINI_URL,
            **overrides,
        }
    )
    client = FakeClient(response)
    return OpenAICompatibleProvider(openai_settings, client=client), client


async def test_tool_doi_sang_khuon_function(settings: Settings) -> None:
    """Tool dùng chung khai theo khuôn Anthropic (``input_schema``); OpenAI đòi ``parameters``."""
    provider, client = _provider(settings, _response())

    await provider.chat([Message(role="user", content="hoi")], tools=ALL_TOOLS)

    tools = client.chat.completions.request["tools"]
    assert len(tools) == len(ALL_TOOLS)
    assert tools[0]["type"] == "function"
    assert tools[0]["function"]["name"] == ALL_TOOLS[0]["name"]
    assert tools[0]["function"]["parameters"] == ALL_TOOLS[0]["input_schema"]


async def test_system_la_mot_luot_trong_messages(settings: Settings) -> None:
    """Khác Anthropic: Chat Completions nhận system như lượt đầu của ``messages``."""
    provider, client = _provider(settings, _response())

    await provider.chat(
        [Message(role="system", content="luat choi"), Message(role="user", content="hoi")]
    )

    sent = client.chat.completions.request["messages"]
    assert sent == [
        {"role": "system", "content": "luat choi"},
        {"role": "user", "content": "hoi"},
    ]


async def test_doc_tool_call_va_so_token(settings: Settings) -> None:
    raw = FakeToolCall(
        {
            "id": "call_abc",
            "type": "function",
            "function": {"name": "get_warning", "arguments": '{"evidence_id": "EV-005E"}'},
        }
    )
    provider, _ = _provider(settings, _response(content=None, tool_calls=[raw]))

    response = await provider.chat([Message(role="user", content="hoi")], tools=ALL_TOOLS)

    assert response.content is None
    assert [(c.id, c.name, c.arguments) for c in response.tool_calls] == [
        ("call_abc", "get_warning", {"evidence_id": "EV-005E"})
    ]
    assert (response.input_tokens, response.output_tokens) == (200, 30)


async def test_truong_la_cua_tool_call_duoc_gui_lai_nguyen_van(settings: Settings) -> None:
    """Gemini 3 đính thought signature vào tool call và đòi gửi lại y nguyên ở lượt sau.

    Tài liệu không chốt nó nằm ở trường nào, nên provider giữ TRỌN mọi trường lạ. Bóc đúng một
    trường đã biết thì hỏng ngay khi phía họ đổi tên — và hỏng bằng lỗi 400 giữa hội thoại.
    """
    signature = {"google": {"thought_signature": "chu-ky-mo-hinh"}}
    raw = FakeToolCall(
        {
            "id": "call_1",
            "type": "function",
            "function": {"name": "get_coverage", "arguments": "{}"},
            "extra_content": signature,
        }
    )
    provider, client = _provider(settings, _response(content=None, tool_calls=[raw]))

    first = await provider.chat([Message(role="user", content="hoi")], tools=ALL_TOOLS)
    await provider.chat(
        [
            Message(role="user", content="hoi"),
            Message(role="assistant", content="", tool_calls=first.tool_calls),
            Message(role="tool", content="ket qua", tool_call_id="call_1", name="get_coverage"),
        ],
        tools=ALL_TOOLS,
    )

    assistant, tool = client.chat.completions.request["messages"][1:]
    assert assistant["content"] is None
    assert assistant["tool_calls"][0]["extra_content"] == signature
    assert assistant["tool_calls"][0]["function"] == {"name": "get_coverage", "arguments": "{}"}
    assert tool == {"role": "tool", "tool_call_id": "call_1", "content": "ket qua"}


async def test_tool_call_id_rong_duoc_dat_id_thay_the(settings: Settings) -> None:
    """Có backend trả id rỗng; lượt ``tool`` phía sau vẫn phải tham chiếu được tới nó."""
    raw = FakeToolCall({"id": "", "function": {"name": "get_warning", "arguments": "{}"}})
    provider, _ = _provider(settings, _response(content=None, tool_calls=[raw]))

    response = await provider.chat([Message(role="user", content="hoi")], tools=ALL_TOOLS)

    assert response.tool_calls[0].id == "call_0"


async def test_arguments_khong_phai_json_khong_lam_sap_vong_lap(settings: Settings) -> None:
    raw = FakeToolCall({"id": "c", "function": {"name": "get_warning", "arguments": "{hong"}})
    provider, _ = _provider(settings, _response(content=None, tool_calls=[raw]))

    response = await provider.chat([Message(role="user", content="hoi")], tools=ALL_TOOLS)

    assert response.tool_calls[0].arguments == {"_raw_arguments": "{hong"}


async def test_nop_bai_bang_structured_output_luoc_do_da_trai_phang(settings: Settings) -> None:
    """Bước nộp dùng ``response_format`` (Gemini có tài liệu), không ép ``tool_choice``.

    Lược đồ phải không còn ``$ref``: hỗ trợ ``$ref`` trong structured output khác nhau giữa các
    backend tương thích OpenAI, và lỗi chỉ lộ ra ở lượt cuối của mỗi câu hỏi.
    """
    provider, client = _provider(settings, _response(content='{"verdict": "x"}'))

    response = await provider.chat(
        [Message(role="user", content="hoi")],
        response_schema=AnalysisResponse.model_json_schema(),
    )

    request = client.chat.completions.request
    assert "tools" not in request
    assert "tool_choice" not in request
    response_format = request["response_format"]
    assert response_format["type"] == "json_schema"
    assert response_format["json_schema"]["name"] == SUBMIT_TOOL_NAME
    assert "$ref" not in json.dumps(response_format) and "$defs" not in json.dumps(response_format)
    assert response.content == '{"verdict": "x"}'


def test_trai_phang_giu_nguyen_noi_dung_dinh_nghia() -> None:
    schema = inline_refs(AnalysisResponse.model_json_schema())

    assert schema["properties"]["verdict"]["enum"] == [
        "LIKELY_TRUE_POSITIVE",
        "LIKELY_FALSE_POSITIVE",
        "INSUFFICIENT_EVIDENCE",
    ]
    assert schema["properties"]["claims"]["items"]["required"] == ["text", "confidence"]


async def test_temperature_chi_gui_khi_co_dat(settings: Settings) -> None:
    """Khác Anthropic: các API này vẫn nhận ``temperature`` — nhưng không đặt thì không gửi."""
    provider, client = _provider(settings, _response())
    await provider.chat([Message(role="user", content="hoi")])
    assert "temperature" not in client.chat.completions.request

    provider, client = _provider(settings, _response(), llm_temperature=0.2)
    await provider.chat([Message(role="user", content="hoi")])
    assert client.chat.completions.request["temperature"] == 0.2


def test_ten_provider_ghi_ro_noi_goi_toi(settings: Settings) -> None:
    """``llm_trace.provider`` phải tách được kết quả chạy trên Gemini với trên OpenAI."""
    provider, _ = _provider(settings, _response())
    assert provider.provider_name == "openai-compatible:generativelanguage.googleapis.com"

    provider, _ = _provider(settings, _response(), llm_base_url=None)
    assert provider.provider_name == "openai-compatible:api.openai.com"


def test_base_url_rong_coi_nhu_khong_dat(settings: Settings) -> None:
    """docker-compose truyền biến chưa đặt dưới dạng chuỗi rỗng."""
    blank = Settings(
        _env_file=None,  # type: ignore[call-arg]
        core_api_url="http://core-backend:8080",  # type: ignore[arg-type]
        service_token="x",
        llm_provider="openai",
        llm_api_key="k",
        llm_model="m",
        llm_base_url="  ",
    )
    assert blank.llm_base_url is None


def test_tool_call_cua_anthropic_khong_bi_anh_huong() -> None:
    """``provider_data`` mặc định rỗng — provider Anthropic không phải biết tới nó."""
    assert ToolCall(id="t", name="get_warning", arguments={}).provider_data == {}


class _GeminiStatusError(Exception):
    """Giả lập ``openai.APIStatusError`` với thân lỗi dạng MẢNG như Gemini trả về."""

    status_code = 503

    def __init__(self, body: Any) -> None:
        super().__init__("Error code: 503")
        self.response = SimpleNamespace(json=lambda: body)


class _RaisingCompletions:
    def __init__(self, exc: Exception) -> None:
        self._exc = exc

    async def create(self, **kwargs: Any) -> Any:
        raise self._exc


async def test_loi_http_cua_gemini_doc_ra_cau_giai_thich_that(settings: Settings) -> None:
    """Gặp thật: giao diện chỉ hiện "Error code: 503" — không biết là quá tải hay khoá sai."""
    body = [{"error": {"code": 503, "message": "high demand", "status": "UNAVAILABLE"}}]
    provider, client = _provider(settings, _response())
    client.chat.completions = _RaisingCompletions(_GeminiStatusError(body))  # type: ignore[assignment]

    try:
        await provider.chat([Message(role="user", content="hoi")])
    except RuntimeError as exc:
        message = str(exc)
    else:
        raise AssertionError("phai nem loi")

    assert message == (
        "openai-compatible:generativelanguage.googleapis.com tra ve HTTP 503: "
        "UNAVAILABLE: high demand"
    )


async def test_loi_khong_phai_http_di_nguyen_ven(settings: Settings) -> None:
    """Lỗi không có ``status_code`` (mất mạng, bug) không bị bọc thành lỗi HTTP giả."""
    provider, client = _provider(settings, _response())
    client.chat.completions = _RaisingCompletions(ConnectionError("dut mang"))  # type: ignore[assignment]

    try:
        await provider.chat([Message(role="user", content="hoi")])
    except ConnectionError:
        pass
    else:
        raise AssertionError("phai nem ConnectionError")
