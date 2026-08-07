"""Sự kiện agent loop phát ra trong lúc chạy.

Đây là thứ đi qua SSE tới người dùng. Nó KHÔNG stream từng token của câu trả lời: đầu ra bị ép
theo cấu trúc, nên một JSON dở dang chẳng đọc được, mà tệ hơn là nó sẽ hiện ra những Evidence ID
chưa qua hậu kiểm — đúng thứ mà cả cơ chế trích dẫn sinh ra để chặn.

Cái được stream là **tiến trình truy xuất bằng chứng**: agent đang gọi tool nào, mất bao lâu,
lấy về bao nhiêu mục. Nó vừa là phản hồi hữu ích lúc chờ, vừa làm cho luận điểm "5 tool là ranh
giới kiến trúc thật" nhìn thấy được bằng mắt thay vì chỉ nằm trong báo cáo.
"""

from enum import StrEnum
from typing import Any, Literal

from pydantic import BaseModel, Field

from agent_service.schemas.analysis import AnalysisResponse


class EventType(StrEnum):
    STATUS = "status"
    TOOL_CALL = "tool_call"
    TOOL_RESULT = "tool_result"
    ANSWER = "answer"
    ERROR = "error"


class StatusEvent(BaseModel):
    type: Literal[EventType.STATUS] = EventType.STATUS
    iteration: int
    message: str


class ToolCallEvent(BaseModel):
    type: Literal[EventType.TOOL_CALL] = EventType.TOOL_CALL
    iteration: int
    name: str
    arguments: dict[str, Any]


class ToolResultEvent(BaseModel):
    """Số đo, không phải payload — payload đi thẳng vào ngữ cảnh của model, không ra giao diện."""

    type: Literal[EventType.TOOL_RESULT] = EventType.TOOL_RESULT
    iteration: int
    name: str
    duration_ms: int
    item_count: int | None = None
    error: str | None = None


class LlmTraceEntry(BaseModel):
    """Số đo một vòng gọi model, để core-backend ghi vào bảng ``llm_trace``."""

    iteration: int
    provider: str
    model: str
    prompt_version: str
    input_tokens: int = 0
    output_tokens: int = 0
    latency_ms: int = 0
    stop_reason: str | None = None
    request_messages: list[dict[str, Any]] = Field(default_factory=list)
    tool_calls: list[dict[str, Any]] = Field(default_factory=list)
    response_content: str | None = None
    error_message: str | None = None


class AnswerEvent(BaseModel):
    """Câu trả lời NGUYÊN BẢN của model — chưa qua hậu kiểm trích dẫn.

    core-backend chạy CitationValidator trên payload này rồi mới phát tiếp cho frontend. Frontend
    không bao giờ nhận trực tiếp sự kiện này (ADR 0010).
    """

    type: Literal[EventType.ANSWER] = EventType.ANSWER
    answer: AnalysisResponse
    traces: list[LlmTraceEntry]


class ErrorEvent(BaseModel):
    type: Literal[EventType.ERROR] = EventType.ERROR
    message: str
    traces: list[LlmTraceEntry] = Field(default_factory=list)


AgentEvent = StatusEvent | ToolCallEvent | ToolResultEvent | AnswerEvent | ErrorEvent
