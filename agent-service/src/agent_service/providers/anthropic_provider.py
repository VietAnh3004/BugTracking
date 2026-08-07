"""Cài đặt :class:`LlmProvider` cho Anthropic.

SDK của hãng chỉ được import ở đây. Mọi phần còn lại của service làm việc với
:class:`~agent_service.providers.base.LlmProvider`, nên thêm provider mới không đụng tới agent
loop — điều kiện để thực nghiệm tuần 15 chạy nhiều provider trên cùng một đoạn mã.
"""

import json
import logging
from typing import Any

from agent_service.config import Settings
from agent_service.providers.base import (
    SUBMIT_TOOL_NAME,
    LlmProvider,
    LlmResponse,
    Message,
    ToolCall,
)

logger = logging.getLogger(__name__)


class AnthropicProvider(LlmProvider):
    """Gọi Messages API của Anthropic."""

    def __init__(self, settings: Settings, client: Any | None = None) -> None:
        # Họ model Claude 5 (và Opus 4.7 trở đi) TỪ CHỐI temperature/top_p/top_k bằng lỗi 400.
        # Nổ ở đây, lúc khởi động, thay vì để hỏng giữa một phiên hội thoại: cùng một nguyên tắc
        # fail-fast mà core-backend áp cho biến môi trường thiếu.
        if settings.llm_temperature is not None:
            raise ValueError(
                "LLM_TEMPERATURE khong dung duoc voi provider anthropic: cac model tu Claude "
                "Opus 4.7 tro di tra loi 400 cho tham so nay. Bo bien nay di; do on dinh giua "
                "cac lan chay duoc bao cao bang cach chay lap roi do phuong sai."
            )

        if client is None:
            from anthropic import AsyncAnthropic

            client = AsyncAnthropic(api_key=settings.llm_api_key)

        self._client = client
        self._model = settings.llm_model
        self._max_tokens = settings.llm_max_tokens

    @property
    def model_name(self) -> str:
        return self._model

    @property
    def provider_name(self) -> str:
        return "anthropic"

    async def chat(
        self,
        messages: list[Message],
        tools: list[dict[str, Any]] | None = None,
        response_schema: dict[str, Any] | None = None,
    ) -> LlmResponse:
        system_text, turns = _split_system(messages)

        request: dict[str, Any] = {
            "model": self._model,
            "max_tokens": self._max_tokens,
            "messages": turns,
        }
        if system_text:
            request["system"] = system_text

        effective_tools = list(tools or [])
        if response_schema is not None:
            effective_tools.append(_submit_tool(response_schema))
            request["tool_choice"] = {"type": "tool", "name": SUBMIT_TOOL_NAME}
        if effective_tools:
            request["tools"] = effective_tools

        response = await self._client.messages.create(**request)
        return _to_llm_response(response, forced_submit=response_schema is not None)


def _submit_tool(schema: dict[str, Any]) -> dict[str, Any]:
    return {
        "name": SUBMIT_TOOL_NAME,
        "description": (
            "Nộp câu trả lời cuối cùng. Mọi nhận định phải kèm Evidence ID lấy từ kết quả tool, "
            "không được bịa."
        ),
        "input_schema": schema,
    }


def _split_system(messages: list[Message]) -> tuple[str, list[dict[str, Any]]]:
    """Tách system prompt ra khỏi hội thoại và gộp các lượt tool liền nhau.

    Anthropic nhận system như một tham số riêng, không phải một lượt trong ``messages``.

    <p>Gộp lượt ``tool`` liền kề là BẮT BUỘC chứ không phải tối ưu: đặc tả yêu cầu mọi
    ``tool_result`` của cùng một lượt nằm trong MỘT message người dùng. Tách ra nhiều message
    khiến model dần thôi gọi tool song song — hỏng âm thầm, chỉ lộ ra ở số vòng lặp tăng dần.
    """
    system_parts: list[str] = []
    turns: list[dict[str, Any]] = []

    for message in messages:
        if message.role == "system":
            system_parts.append(message.content)
        elif message.role == "user":
            turns.append({"role": "user", "content": [{"type": "text", "text": message.content}]})
        elif message.role == "assistant":
            turns.append({"role": "assistant", "content": _assistant_blocks(message)})
        else:
            block = {
                "type": "tool_result",
                "tool_use_id": message.tool_call_id,
                "content": message.content,
            }
            if turns and turns[-1]["role"] == "user" and _is_tool_result_turn(turns[-1]):
                turns[-1]["content"].append(block)
            else:
                turns.append({"role": "user", "content": [block]})

    return "\n\n".join(system_parts), turns


def _is_tool_result_turn(turn: dict[str, Any]) -> bool:
    content = turn["content"]
    return bool(content) and all(block.get("type") == "tool_result" for block in content)


def _assistant_blocks(message: Message) -> list[dict[str, Any]]:
    blocks: list[dict[str, Any]] = []
    if message.content:
        blocks.append({"type": "text", "text": message.content})
    for call in message.tool_calls:
        blocks.append(
            {"type": "tool_use", "id": call.id, "name": call.name, "input": call.arguments}
        )
    # Một lượt assistant rỗng bị API từ chối; chuyện này chỉ xảy ra khi model trả về đúng một
    # khối rỗng, nhưng nếu xảy ra thì lỗi phải đọc được thay vì là 400 từ phía hãng.
    if not blocks:
        blocks.append({"type": "text", "text": "(khong co noi dung)"})
    return blocks


def _to_llm_response(response: Any, forced_submit: bool) -> LlmResponse:
    """Chuyển phản hồi của SDK về kiểu chung.

    Khi ép nộp qua ``submit_analysis``, JSON của tool đó CHÍNH LÀ câu trả lời cuối — nó đi vào
    ``content`` chứ không vào ``tool_calls``, để agent loop không đi thêm một vòng vô nghĩa.
    """
    text_parts: list[str] = []
    tool_calls: list[ToolCall] = []
    submitted: str | None = None

    for block in response.content:
        if block.type == "text":
            text_parts.append(block.text)
        elif block.type == "tool_use":
            if forced_submit and block.name == SUBMIT_TOOL_NAME:
                submitted = json.dumps(block.input, ensure_ascii=False)
            else:
                tool_calls.append(
                    ToolCall(id=block.id, name=block.name, arguments=dict(block.input))
                )

    content = submitted if submitted is not None else ("\n".join(text_parts) or None)
    usage = getattr(response, "usage", None)
    return LlmResponse(
        content=content,
        tool_calls=tool_calls,
        input_tokens=getattr(usage, "input_tokens", 0) or 0,
        output_tokens=getattr(usage, "output_tokens", 0) or 0,
        stop_reason=getattr(response, "stop_reason", None),
    )
