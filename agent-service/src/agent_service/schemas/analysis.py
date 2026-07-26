"""Lược đồ phản hồi của agent.

Agent không trả về văn bản tự do. Nó bị buộc sinh ra cấu trúc dưới đây, trong đó **mỗi nhận
định phải kèm danh sách Evidence ID**. Nhận định không gắn được bằng chứng hợp lệ sẽ bị
core-backend hạ xuống ``INSUFFICIENT_EVIDENCE`` ở bước hậu kiểm.

Claim được giữ lại nguyên vẹn kể cả khi bị đánh dấu không có căn cứ — chúng là dữ liệu để tính
Unsupported Claim Rate trong thực nghiệm, xoá đi là mất số liệu.
"""

from enum import StrEnum

from pydantic import BaseModel, Field


class Verdict(StrEnum):
    """Kết luận của agent về một cảnh báo phân tích tĩnh."""

    LIKELY_TRUE_POSITIVE = "LIKELY_TRUE_POSITIVE"
    LIKELY_FALSE_POSITIVE = "LIKELY_FALSE_POSITIVE"
    INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE"


class Claim(BaseModel):
    """Một nhận định đơn lẻ kèm bằng chứng chống lưng."""

    text: str = Field(..., description="Nội dung nhận định, một ý duy nhất")
    evidence_ids: list[str] = Field(
        default_factory=list,
        description="Evidence ID dạng ngắn (ví dụ EV-3F2A) hỗ trợ nhận định này",
    )
    confidence: float = Field(..., ge=0.0, le=1.0)


class AnalysisResponse(BaseModel):
    """Phản hồi hoàn chỉnh cho một câu hỏi về cảnh báo."""

    verdict: Verdict
    summary: str = Field(..., description="Trả lời ngắn gọn cho người dùng")
    claims: list[Claim] = Field(default_factory=list)
    contradicting_evidence_ids: list[str] = Field(
        default_factory=list,
        description="Bằng chứng đi ngược lại kết luận; để trống nếu không có",
    )
    suggested_next_checks: list[str] = Field(
        default_factory=list,
        description="Việc người dùng nên kiểm tra thêm trước khi quyết định",
    )
