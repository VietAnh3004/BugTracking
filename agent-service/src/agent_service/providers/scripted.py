"""Provider giả, trả về một kịch bản đã định sẵn.

Không phải "mock cho có": mọi test của agent loop chạy trên lớp này, và quy ước của repo là
KHÔNG BAO GIỜ gọi LLM thật trong test — vừa tốn tiền vừa khiến kết quả không lặp lại được.

Lớp này cũng dùng được để nghiệm thu end-to-end không cần khoá API: đặt ``LLM_PROVIDER=scripted``
thì toàn bộ đường đi từ frontend qua core-backend tới agent-service và ngược lại chạy thật, chỉ
riêng model là giả.
"""

import json
from typing import Any

from agent_service.config import Settings
from agent_service.providers.base import LlmProvider, LlmResponse, Message

# Kịch bản mặc định khi chạy LLM_PROVIDER=scripted mà không nạp kịch bản riêng. Đủ để nghiệm thu
# đường đi, không giả vờ là suy luận thật.
_DEFAULT_ANSWER: dict[str, Any] = {
    "verdict": "INSUFFICIENT_EVIDENCE",
    "summary": "Provider gia dang duoc dung — day khong phai ket luan cua mot mo hinh that.",
    "claims": [],
    "contradicting_evidence_ids": [],
    "suggested_next_checks": ["Cau hinh LLM_PROVIDER=anthropic kem LLM_API_KEY that."],
}


class ScriptedProvider(LlmProvider):
    """Trả lần lượt các phản hồi đã nạp sẵn, và ghi lại mọi lượt gọi để test kiểm."""

    def __init__(
        self,
        responses: list[LlmResponse] | None = None,
        model: str = "scripted-model",
        repeat_last: bool = False,
    ) -> None:
        self._responses = list(responses) if responses else []
        self._model = model
        self._repeat_last = repeat_last
        self.calls: list[dict[str, Any]] = []

    @classmethod
    def from_settings(cls, settings: Settings) -> "ScriptedProvider":
        """Bản dùng khi CHẠY THẬT với ``LLM_PROVIDER=scripted``.

        ``repeat_last=True`` là bắt buộc chứ không phải tiện tay: một lượt hỏi đáp gọi model ÍT
        NHẤT hai lần (vòng truy xuất, rồi lượt nộp có ép lược đồ). Một kịch bản một phản hồi sẽ
        chết ở lượt thứ hai — và chết đúng ở đường mà tài liệu bảo dùng để nghiệm thu end-to-end.
        Chỉ phát hiện được bằng cách chạy thật; không test nào của agent loop đi qua nhánh này.
        """
        return cls(
            responses=[LlmResponse(content=json.dumps(_DEFAULT_ANSWER, ensure_ascii=False))],
            model=settings.llm_model,
            repeat_last=True,
        )

    @property
    def model_name(self) -> str:
        return self._model

    @property
    def provider_name(self) -> str:
        return "scripted"

    async def chat(
        self,
        messages: list[Message],
        tools: list[dict[str, Any]] | None = None,
        response_schema: dict[str, Any] | None = None,
    ) -> LlmResponse:
        self.calls.append(
            {
                "messages": [m.model_dump() for m in messages],
                "tool_names": [t["name"] for t in tools or []],
                "forced_submit": response_schema is not None,
            }
        )
        if len(self._responses) == 1 and self._repeat_last:
            return self._responses[0]
        if not self._responses:
            # Trong TEST (repeat_last=False) hết kịch bản nghĩa là agent loop chạy nhiều vòng hơn
            # test dự liệu. Ném ra chứ đừng lặp lại phản hồi cuối: lặp lại sẽ biến một vòng lặp
            # vô hạn thành test xanh.
            raise AssertionError(
                f"ScriptedProvider het kich ban o luot thu {len(self.calls)} — agent loop chay "
                "nhieu vong hon test du lieu."
            )
        return self._responses.pop(0)
