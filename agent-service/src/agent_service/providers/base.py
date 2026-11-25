"""Lớp trừu tượng cho nhà cung cấp LLM.

Vòng lặp agent chỉ làm việc với giao diện này, nên đổi Anthropic sang OpenAI hay model chạy
cục bộ không phải sửa logic suy luận. Thực nghiệm cũng nhờ đó chạy được trên nhiều provider
với cùng một đoạn mã.
"""

from abc import ABC, abstractmethod
from typing import Any, Literal

from pydantic import BaseModel, Field

Role = Literal["system", "user", "assistant", "tool"]

# Tên tool tổng hợp mà provider tự thêm vào khi cần ép định dạng đầu ra. Ép bằng tool-call thay
# vì bằng cơ chế structured-output riêng của từng hãng là chủ ý: nó chạy như nhau trên cả ba
# provider, nên thực nghiệm tuần 15 so được kết quả giữa chúng mà không đổi mã.
SUBMIT_TOOL_NAME = "submit_analysis"


class ToolCall(BaseModel):
    """Yêu cầu gọi tool do model phát ra."""

    id: str
    name: str
    arguments: dict[str, Any]
    # Dữ liệu riêng của provider, gửi trả NGUYÊN VĂN ở lượt sau. Agent loop không đọc nó. Vì
    # sao cần: Gemini 3 qua endpoint tương thích OpenAI đính "thought signature" vào tool call và
    # đòi gửi lại y nguyên, nhưng tài liệu không chốt nó nằm ở trường nào — giữ trọn mọi trường
    # lạ thì đúng bất kể vị trí, còn bóc đúng một trường thì hỏng ngay khi họ đổi tên.
    provider_data: dict[str, Any] = Field(default_factory=dict)


class Message(BaseModel):
    """Một lượt trong hội thoại gửi tới model."""

    role: Role
    content: str
    # Chỉ có ở lượt ``assistant``. Phải giữ lại khi ghi lịch sử hội thoại: bỏ đi thì lượt
    # ``tool`` phía sau không còn tham chiếu được tới yêu cầu đã sinh ra nó, và mọi provider
    # đều từ chối cặp không khớp.
    tool_calls: list[ToolCall] = Field(default_factory=list)
    # Chỉ có ở lượt ``tool``: id của tool call mà lượt này trả lời.
    tool_call_id: str | None = None
    name: str | None = None


class LlmResponse(BaseModel):
    """Kết quả một lượt gọi model.

    ``tool_calls`` rỗng nghĩa là model đã đủ thông tin và ``content`` là câu trả lời cuối.
    """

    content: str | None
    tool_calls: list[ToolCall] = Field(default_factory=list)
    input_tokens: int = 0
    output_tokens: int = 0
    stop_reason: str | None = None


class LlmProvider(ABC):
    """Giao diện chung cho mọi nhà cung cấp LLM."""

    @abstractmethod
    async def chat(
        self,
        messages: list[Message],
        tools: list[dict[str, Any]] | None = None,
        response_schema: dict[str, Any] | None = None,
    ) -> LlmResponse:
        """Gửi hội thoại tới model và nhận về một lượt trả lời.

        Args:
            messages: Lịch sử hội thoại, phần tử đầu thường là system prompt.
            tools: Khai báo tool theo JSON Schema; ``None`` nghĩa là cấm gọi tool.
                Cấu hình LLM-Code và LLM-Static trong thực nghiệm truyền ``None`` ở đây.
            response_schema: JSON Schema ép định dạng đầu ra. Khi có, provider ép model gọi
                đúng tool ``submit_analysis`` và trả JSON đó trong ``content``.

        Returns:
            Nội dung trả lời hoặc danh sách tool cần gọi, kèm số token đã dùng.
        """

    @property
    @abstractmethod
    def model_name(self) -> str:
        """Định danh model, ghi kèm vào log để truy vết kết quả thực nghiệm."""

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Tên nhà cung cấp, ghi vào ``llm_trace`` cùng với model."""


class RetryableLlmError(RuntimeError):
    """Lỗi TẠM THỜI khi gọi model (quá tải, vượt tần suất, hết giờ chờ, đứt kết nối) — thử lại được.

    Agent loop tự thử lại thay vì để SDK làm ngầm (sửa 24/11/2026): SDK thử lại trong im lặng nên
    gặp thật một lượt Gemini chờ 503 suốt nhiều phút mà giao diện không hiện gì, người dùng tưởng
    treo. Tự thử lại thì mỗi lần thử phát được một sự kiện tiến trình.

    ``str(exc)`` là chi tiết kỹ thuật (ghi vào ``llm_trace``); ``reason`` là câu cho người đọc.
    """

    def __init__(self, message: str, reason: str, retry_after: float | None = None) -> None:
        super().__init__(message)
        self.reason = reason
        self.retry_after = retry_after


def classify_transient(
    exc: Exception, message: str, timeout_seconds: float | None
) -> RetryableLlmError | None:
    """Lỗi của SDK (OpenAI hoặc Anthropic) có phải lỗi tạm thời không; không phải thì ``None``.

    Nhận dạng theo TÊN lớp và ``status_code`` thay vì import SDK: hai SDK cùng đặt tên
    ``APITimeoutError``/``APIConnectionError``, và lớp dùng chung này không được kéo SDK nào vào.
    Cùng tập mã trạng thái mà hai SDK tự thử lại: 408, 409, 429, 5xx.
    """
    name = type(exc).__name__
    if name == "APITimeoutError":
        wait = f" trong {timeout_seconds:.0f} giây" if timeout_seconds else ""
        return RetryableLlmError(message, f"Model không phản hồi{wait}")
    if name == "APIConnectionError":
        return RetryableLlmError(message, "Mất kết nối tới máy chủ model")
    status = getattr(exc, "status_code", None)
    if not isinstance(status, int):
        return None
    if status == 429:
        reason = "Vượt giới hạn tần suất gọi model (HTTP 429)"
    elif status >= 500:
        reason = f"Máy chủ model quá tải hoặc lỗi tạm thời (HTTP {status})"
    elif status in (408, 409):
        reason = f"Máy chủ model từ chối tạm thời (HTTP {status})"
    else:
        return None
    return RetryableLlmError(message, reason, _retry_after(exc))


def _retry_after(exc: Exception) -> float | None:
    """Giá trị ``Retry-After`` (giây) nếu máy chủ gửi kèm; dạng ngày giờ thì bỏ qua."""
    headers = getattr(getattr(exc, "response", None), "headers", None)
    raw = headers.get("retry-after") if headers is not None else None
    try:
        return float(raw) if raw is not None else None
    except (TypeError, ValueError):
        return None
