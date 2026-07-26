"""Lớp trừu tượng cho nhà cung cấp LLM.

Vòng lặp agent chỉ làm việc với giao diện này, nên đổi Anthropic sang OpenAI hay model chạy
cục bộ không phải sửa logic suy luận. Thực nghiệm cũng nhờ đó chạy được trên nhiều provider
với cùng một đoạn mã.
"""

from abc import ABC, abstractmethod
from typing import Any, Literal

from pydantic import BaseModel

Role = Literal["system", "user", "assistant", "tool"]


class Message(BaseModel):
    """Một lượt trong hội thoại gửi tới model."""

    role: Role
    content: str
    tool_call_id: str | None = None
    name: str | None = None


class ToolCall(BaseModel):
    """Yêu cầu gọi tool do model phát ra."""

    id: str
    name: str
    arguments: dict[str, Any]


class LlmResponse(BaseModel):
    """Kết quả một lượt gọi model.

    ``tool_calls`` rỗng nghĩa là model đã đủ thông tin và ``content`` là câu trả lời cuối.
    """

    content: str | None
    tool_calls: list[ToolCall] = []
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
            response_schema: JSON Schema ép định dạng đầu ra khi không còn tool nào được gọi.

        Returns:
            Nội dung trả lời hoặc danh sách tool cần gọi, kèm số token đã dùng.
        """

    @property
    @abstractmethod
    def model_name(self) -> str:
        """Định danh model, ghi kèm vào log để truy vết kết quả thực nghiệm."""
