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
