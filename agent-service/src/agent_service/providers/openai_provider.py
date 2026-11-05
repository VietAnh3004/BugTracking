"""Cài đặt :class:`LlmProvider` cho mọi API tương thích Chat Completions của OpenAI.

Một lớp cho nhiều nơi: đổi ``LLM_BASE_URL`` là trỏ được sang Google Gemini
(``https://generativelanguage.googleapis.com/v1beta/openai/``), Groq, OpenRouter, Ollama chạy
cục bộ, hay chính OpenAI. SDK ``openai`` chỉ được import ở đây.

Hai chỗ cố ý KHÁC :class:`~agent_service.providers.anthropic_provider.AnthropicProvider`:

- **Nộp câu trả lời bằng structured output (``response_format``), không bằng ép tool-call.**
  Ép ``tool_choice`` vào một hàm cụ thể không được tài liệu Gemini OpenAI-compat nhắc tới, còn
  structured output thì có. Mỗi provider dùng cơ chế mà nơi nó gọi tới hỗ trợ chắc chắn; agent
  loop không biết khác biệt này vì cả hai đều trả JSON nộp bài trong ``content``.
- **``temperature`` được gửi nếu có đặt.** Các API này vẫn nhận tham số đó; chỉ họ model Claude
  mới từ chối.
"""

import json
import logging
from typing import Any
from urllib.parse import urlparse

from agent_service.config import Settings
from agent_service.providers.base import (
    SUBMIT_TOOL_NAME,
    LlmProvider,
    LlmResponse,
    Message,
    ToolCall,
)

logger = logging.getLogger(__name__)


class OpenAICompatibleProvider(LlmProvider):
    """Gọi ``POST {base_url}/chat/completions``."""

    def __init__(self, settings: Settings, client: Any | None = None) -> None:
        if client is None:
            from openai import AsyncOpenAI

            client = AsyncOpenAI(
                api_key=settings.llm_api_key,
                base_url=settings.llm_base_url,
                timeout=settings.request_timeout_seconds,
                max_retries=settings.llm_max_retries,
            )

        self._client = client
        self._model = settings.llm_model
        self._max_tokens = settings.llm_max_tokens
        self._temperature = settings.llm_temperature
        # Ghi vào llm_trace.provider. Kèm host vì "openai" trần không cho biết thực nghiệm đã
        # chạy trên Gemini hay trên OpenAI — mà đó là thứ phải tách được khi đọc số liệu.
        host = urlparse(settings.llm_base_url).hostname if settings.llm_base_url else None
        self._provider_name = f"openai-compatible:{host or 'api.openai.com'}"

    @property
    def model_name(self) -> str:
        return self._model

    @property
    def provider_name(self) -> str:
        return self._provider_name

    async def chat(
        self,
        messages: list[Message],
        tools: list[dict[str, Any]] | None = None,
        response_schema: dict[str, Any] | None = None,
    ) -> LlmResponse:
        request: dict[str, Any] = {
            "model": self._model,
            "messages": [_to_openai_message(m) for m in messages],
            "max_tokens": self._max_tokens,
        }
        if self._temperature is not None:
            request["temperature"] = self._temperature
        if tools:
            request["tools"] = [_to_openai_tool(t) for t in tools]
        if response_schema is not None:
            request["response_format"] = {
                "type": "json_schema",
                "json_schema": {"name": SUBMIT_TOOL_NAME, "schema": inline_refs(response_schema)},
            }

        try:
            response = await self._client.chat.completions.create(**request)
        except Exception as exc:
            status = getattr(exc, "status_code", None)
            if status is None:
                raise
            raise ProviderHttpError(
                f"{self._provider_name} tra ve HTTP {status}: {_error_detail(exc)}"
            ) from exc
        return _to_llm_response(response)


class ProviderHttpError(RuntimeError):
    """Lỗi HTTP từ API của hãng, đã kèm câu giải thích đọc được."""


def _error_detail(exc: Exception) -> str:
    """Bóc câu giải thích thật từ thân lỗi.

    SDK ``openai`` chỉ đọc được thân lỗi dạng ``{"error": {...}}``. Gemini trả một MẢNG
    (``[{"error": {...}}]``), nên thông điệp của SDK chỉ còn "Error code: 503" — người dùng
    không biết là model quá tải (thử lại sau) hay khoá sai (phải sửa cấu hình).
    """
    response = getattr(exc, "response", None)
    try:
        body: Any = response.json() if response is not None else None
    except ValueError:
        body = None
    if isinstance(body, list) and body:
        body = body[0]
    error = body.get("error") if isinstance(body, dict) else None
    if isinstance(error, dict) and error.get("message"):
        status = error.get("status")
        return f"{status}: {error['message']}" if status else str(error["message"])
    return str(exc)


def _to_openai_tool(tool: dict[str, Any]) -> dict[str, Any]:
    """Khai báo tool dùng chung (khuôn Anthropic: ``input_schema``) sang khuôn ``function``."""
    return {
        "type": "function",
        "function": {
            "name": tool["name"],
            "description": tool.get("description", ""),
            "parameters": tool["input_schema"],
        },
    }


def _to_openai_message(message: Message) -> dict[str, Any]:
    if message.role == "tool":
        return {"role": "tool", "tool_call_id": message.tool_call_id, "content": message.content}

    if message.role == "assistant" and message.tool_calls:
        return {
            "role": "assistant",
            # Một số backend từ chối chuỗi rỗng đi kèm tool_calls; null là dạng chuẩn.
            "content": message.content or None,
            "tool_calls": [_to_openai_tool_call(call) for call in message.tool_calls],
        }

    return {"role": message.role, "content": message.content}


def _to_openai_tool_call(call: ToolCall) -> dict[str, Any]:
    # provider_data trước, các trường chuẩn sau: trường chuẩn luôn thắng, còn mọi trường lạ
    # (thought signature của Gemini) đi kèm nguyên văn.
    return {
        **call.provider_data,
        "id": call.id,
        "type": "function",
        "function": {
            "name": call.name,
            "arguments": json.dumps(call.arguments, ensure_ascii=False),
        },
    }


def _to_llm_response(response: Any) -> LlmResponse:
    choice = response.choices[0]
    message = choice.message

    tool_calls = [
        _from_openai_tool_call(raw, index) for index, raw in enumerate(message.tool_calls or [])
    ]

    usage = getattr(response, "usage", None)
    return LlmResponse(
        content=message.content or None,
        tool_calls=tool_calls,
        input_tokens=getattr(usage, "prompt_tokens", 0) or 0,
        output_tokens=getattr(usage, "completion_tokens", 0) or 0,
        stop_reason=getattr(choice, "finish_reason", None),
    )


def _from_openai_tool_call(raw: Any, index: int) -> ToolCall:
    data: dict[str, Any] = raw.model_dump(exclude_none=True) if hasattr(raw, "model_dump") else raw
    function = data.get("function") or {}
    arguments_text = function.get("arguments") or "{}"
    try:
        arguments = json.loads(arguments_text)
    except json.JSONDecodeError:
        # Không bịa tham số: chuyển nguyên chuỗi để lỗi hiện ra ở kết quả tool, nơi model đọc
        # được và tự sửa ở vòng sau.
        logger.warning("tool call %s co arguments khong phai JSON", function.get("name"))
        arguments = {"_raw_arguments": arguments_text}

    extras = {k: v for k, v in data.items() if k not in {"id", "type", "function"}}
    return ToolCall(
        # Có backend trả id rỗng; lượt tool phía sau phải tham chiếu được tới nó.
        id=data.get("id") or f"call_{index}",
        name=function.get("name", ""),
        arguments=arguments if isinstance(arguments, dict) else {"value": arguments},
        provider_data=extras,
    )


def inline_refs(schema: dict[str, Any]) -> dict[str, Any]:
    """Thay mọi ``{"$ref": "#/$defs/X"}`` bằng chính định nghĩa X, rồi bỏ ``$defs``.

    Pydantic sinh ``$defs``/``$ref``, nhưng hỗ trợ ``$ref`` trong structured output khác nhau
    giữa các backend tương thích OpenAI. Lược đồ câu trả lời không đệ quy nên trải phẳng được
    trọn vẹn, và lược đồ phẳng thì nơi nào cũng nhận.
    """
    defs: dict[str, Any] = schema.get("$defs", {})

    def resolve(node: Any, seen: frozenset[str]) -> Any:
        if isinstance(node, dict):
            ref = node.get("$ref")
            if isinstance(ref, str) and ref.startswith("#/$defs/"):
                name = ref.removeprefix("#/$defs/")
                if name in seen:
                    raise ValueError(f"luoc do de quy qua '{name}', khong trai phang duoc")
                merged = {**defs[name], **{k: v for k, v in node.items() if k != "$ref"}}
                return resolve(merged, seen | {name})
            return {k: resolve(v, seen) for k, v in node.items() if k != "$defs"}
        if isinstance(node, list):
            return [resolve(item, seen) for item in node]
        return node

    result: dict[str, Any] = resolve(schema, frozenset())
    return result
