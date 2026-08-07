"""Vòng lặp agent: truy xuất bằng chứng bằng tool-call, rồi nộp câu trả lời có cấu trúc.

Hình dạng vòng lặp bám đúng mục 3.4 của đề cương: nhận câu hỏi + phạm vi → gọi tool → dựng
prompt có Evidence ID → sinh đầu ra có cấu trúc. Bước hậu kiểm trích dẫn KHÔNG ở đây mà ở
core-backend, để nó độc lập với LLM (ADR 0001, ADR 0010).
"""

import logging
import time
from collections.abc import AsyncIterator
from typing import Any

from pydantic import ValidationError

from agent_service.agent.events import (
    AgentEvent,
    AnswerEvent,
    ErrorEvent,
    LlmTraceEntry,
    StatusEvent,
    ToolCallEvent,
    ToolResultEvent,
)
from agent_service.config import Settings
from agent_service.prompts.loader import render
from agent_service.providers.base import LlmProvider, Message
from agent_service.schemas.analysis import AnalysisResponse
from agent_service.tools.evidence_client import EvidenceToolClient
from agent_service.tools.schemas import ALL_TOOLS

logger = logging.getLogger(__name__)

_ANSWER_SCHEMA: dict[str, Any] = AnalysisResponse.model_json_schema()

_BUDGET_EXHAUSTED = (
    "Bạn đã dùng hết số vòng truy xuất cho phép. Hãy nộp câu trả lời dựa trên bằng chứng đã có, "
    "và nói rõ trong `suggested_next_checks` những gì còn chưa kiểm được."
)

_CONTEXT_EXHAUSTED = (
    "\n\n[Đã cắt: ngân sách bằng chứng của phiên này đã hết, phần còn lại không được đưa vào. "
    "Hãy kết luận dựa trên phần đã có, và ghi phần chưa xem được vào `suggested_next_checks`.]"
)


class AgentLoop:
    """Chạy một lượt hỏi đáp, phát sự kiện tiến trình rồi phát câu trả lời."""

    def __init__(
        self, provider: LlmProvider, tool_client: EvidenceToolClient, settings: Settings
    ) -> None:
        self._provider = provider
        self._tools = tool_client
        self._max_iterations = settings.max_tool_iterations
        self._prompt_version = settings.prompt_version
        self._context_budget = settings.evidence_context_budget

    async def run(
        self,
        analysis_run_id: str,
        question: str,
        focus_short_id: str | None = None,
        history: list[dict[str, str]] | None = None,
    ) -> AsyncIterator[AgentEvent]:
        messages = [
            Message(role="system", content=render("system.jinja2")),
            Message(
                role="user",
                content=render(
                    "user_question.jinja2",
                    question=question,
                    focus_short_id=focus_short_id,
                    history=history or [],
                ),
            ),
        ]
        traces: list[LlmTraceEntry] = []

        try:
            async for event in self._retrieve(messages, traces, analysis_run_id):
                yield event
            yield await self._submit(messages, traces)
        except Exception as exc:
            logger.exception("agent loop hong o vong %s", len(traces))
            yield ErrorEvent(message=str(exc), traces=traces)

    async def _retrieve(
        self, messages: list[Message], traces: list[LlmTraceEntry], analysis_run_id: str
    ) -> AsyncIterator[AgentEvent]:
        """Vòng lặp tool-call, dừng khi model thôi gọi tool hoặc hết ngân sách vòng lặp."""
        spent = 0
        for iteration in range(self._max_iterations):
            yield StatusEvent(iteration=iteration, message="Đang suy luận")

            response, trace = await self._call_model(messages, iteration, tools=ALL_TOOLS)
            traces.append(trace)

            if not response.tool_calls:
                # Model đã đủ thông tin. Giữ lại phần văn bản nó vừa viết: bước nộp phía sau
                # chỉ định dạng lại kết luận này, không suy luận lại từ đầu.
                if response.content:
                    messages.append(Message(role="assistant", content=response.content))
                return

            messages.append(
                Message(
                    role="assistant", content=response.content or "", tool_calls=response.tool_calls
                )
            )

            for call in response.tool_calls:
                yield ToolCallEvent(iteration=iteration, name=call.name, arguments=call.arguments)
                outcome = await self._tools.call(call.name, call.arguments, analysis_run_id)
                trace.tool_calls.append(outcome.as_trace_entry())
                text, spent = self._fit_budget(outcome.as_tool_result_text(), spent)
                messages.append(
                    Message(
                        role="tool",
                        content=text,
                        tool_call_id=call.id,
                        name=call.name,
                    )
                )
                yield ToolResultEvent(
                    iteration=iteration,
                    name=call.name,
                    duration_ms=outcome.duration_ms,
                    item_count=outcome.item_count,
                    error=outcome.error,
                )

        # Hết ngân sách vòng lặp mà model vẫn muốn gọi tool. Không im lặng cắt ngang: nói thẳng
        # cho model biết để nó nộp thứ nó đang có, kèm phần còn thiếu.
        logger.warning("agent dung o gioi han %s vong truy xuat", self._max_iterations)
        messages.append(Message(role="user", content=_BUDGET_EXHAUSTED))

    def _fit_budget(self, text: str, spent: int) -> tuple[str, int]:
        """Cắt kết quả tool cho vừa ngân sách bằng chứng của cả phiên.

        Ngân sách này là thật chứ không phải phòng xa. Trên vulnshop một cảnh báo có tới 52 liên
        kết ``CONTRADICTS``; nhồi hết vào ngữ cảnh vừa tốn tiền vừa làm loãng đúng những bằng
        chứng mạnh mà mô hình cần đọc kỹ — mà đó lại chính là cái bẫy trung tâm của case study.

        Cắt thì **nói ra**. Cắt im lặng khiến mô hình tưởng nó đã xem hết bằng chứng và kết luận
        chắc nịch trên một nửa dữ liệu — tệ hơn hẳn so với việc nó biết mình còn thiếu và ghi vào
        ``suggested_next_checks``.
        """
        remaining = self._context_budget - spent
        if remaining <= 0:
            return _CONTEXT_EXHAUSTED.strip(), spent
        if len(text) <= remaining:
            return text, spent + len(text)
        return text[:remaining] + _CONTEXT_EXHAUSTED, self._context_budget

    async def _submit(self, messages: list[Message], traces: list[LlmTraceEntry]) -> AgentEvent:
        """Một lượt gọi cuối, ép model nộp đúng cấu trúc :class:`AnalysisResponse`.

        Tách riêng khỏi vòng lặp vì ép định dạng và cho phép gọi tool loại trừ nhau: ép nộp
        nghĩa là buộc model gọi đúng một tool, nên nếu bật từ vòng đầu thì nó không bao giờ
        truy xuất được bằng chứng nào.
        """
        iteration = len(traces)
        response, trace = await self._call_model(
            messages, iteration, response_schema=_ANSWER_SCHEMA
        )
        traces.append(trace)

        if not response.content:
            trace.error_message = "model khong nop duoc cau tra loi co cau truc"
            return ErrorEvent(message=trace.error_message, traces=traces)

        try:
            answer = AnalysisResponse.model_validate_json(response.content)
        except ValidationError as exc:
            # Đầu ra sai lược đồ là lỗi thật, không được chữa cháy bằng cách bịa một câu trả
            # lời rỗng: một verdict tự chế sẽ đi thẳng vào số liệu tuần 15.
            trace.error_message = f"phan hoi khong dung luoc do: {exc.error_count()} loi"
            logger.warning("phan hoi khong dung luoc do: %s", exc)
            return ErrorEvent(message=trace.error_message, traces=traces)

        return AnswerEvent(answer=answer, traces=traces)

    async def _call_model(
        self,
        messages: list[Message],
        iteration: int,
        tools: list[dict[str, Any]] | None = None,
        response_schema: dict[str, Any] | None = None,
    ) -> tuple[Any, LlmTraceEntry]:
        started = time.monotonic()
        response = await self._provider.chat(messages, tools=tools, response_schema=response_schema)
        latency_ms = int((time.monotonic() - started) * 1000)

        trace = LlmTraceEntry(
            iteration=iteration,
            provider=self._provider.provider_name,
            model=self._provider.model_name,
            prompt_version=self._prompt_version,
            input_tokens=response.input_tokens,
            output_tokens=response.output_tokens,
            latency_ms=latency_ms,
            stop_reason=response.stop_reason,
            request_messages=[_trace_message(m) for m in messages],
            response_content=response.content,
        )
        return response, trace


def _trace_message(message: Message) -> dict[str, Any]:
    """Dạng gọn của một lượt để ghi vào ``llm_trace.request_messages``.

    Kết quả tool bị cắt bớt: một lần ``get_code_context`` trả về cả method, nhân với số vòng và
    số lượt thì bảng trace phình nhanh hơn mọi bảng khác trong hệ thống. Giữ đủ để đọc lại
    được model đã thấy gì, không giữ nguyên xi.
    """
    content = message.content
    if message.role == "tool" and len(content) > 2000:
        content = content[:2000] + f"… (cat bot, tong {len(message.content)} ky tu)"
    entry: dict[str, Any] = {"role": message.role, "content": content}
    if message.tool_calls:
        entry["tool_calls"] = [
            {"id": c.id, "name": c.name, "arguments": c.arguments} for c in message.tool_calls
        ]
    if message.tool_call_id:
        entry["tool_call_id"] = message.tool_call_id
    return entry
