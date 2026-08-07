"""Hợp đồng HTTP của agent-service.

Người gọi DUY NHẤT là core-backend, server-to-server. Frontend không bao giờ gọi thẳng vào đây:
câu trả lời phải đi qua hậu kiểm trích dẫn trước khi tới người dùng (ADR 0010).
"""

from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field


class HistoryTurn(BaseModel):
    """Một lượt đã có trong hội thoại, do core-backend đọc từ bảng ``message``."""

    role: Literal["USER", "ASSISTANT"]
    content: str


class AnalyzeRequest(BaseModel):
    """Một lượt hỏi.

    ``analysis_run_id`` là bắt buộc và không có mặc định: nó là phạm vi mà Evidence ID có nghĩa,
    và mọi tool call đều được điền id này thay vì để model tự chọn.
    """

    analysis_run_id: UUID
    question: str = Field(..., min_length=1)
    # Cảnh báo đang bàn tới. Không bắt buộc: người dùng hỏi được ở phạm vi cả run.
    focus_short_id: str | None = None
    history: list[HistoryTurn] = Field(default_factory=list)
