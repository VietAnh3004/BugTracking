"""Vòng lặp agent: truy xuất bằng chứng bằng tool-call, rồi nộp câu trả lời có cấu trúc.

Hình dạng vòng lặp bám đúng mục 3.4 của đề cương: nhận câu hỏi + phạm vi → gọi tool → dựng
prompt có Evidence ID → sinh đầu ra có cấu trúc. Bước hậu kiểm trích dẫn KHÔNG ở đây mà ở
core-backend, để nó độc lập với LLM (ADR 0001, ADR 0010).
"""

import asyncio
import logging
import random
import time
from collections.abc import AsyncIterator
from typing import Any

from pydantic import ValidationError

from agent_service.agent.events import (
    AgentEvent,
    AnswerEvent,
    ErrorEvent,
    LlmTraceEntry,
    RetryEvent,
    StatusEvent,
    ToolCallEvent,
    ToolResultEvent,
)
from agent_service.config import Settings
from agent_service.prompts.loader import render
from agent_service.providers.base import LlmProvider, Message, RetryableLlmError
from agent_service.schemas.analysis import AnalysisResponse
from agent_service.schemas.api import ExperimentConfig
from agent_service.tools.evidence_client import EvidenceToolClient
from agent_service.tools.schemas import ALL_TOOLS

logger = logging.getLogger(__name__)

# Gián tiếp qua biến module để test thay được, khỏi phải ngủ thật.
_sleep = asyncio.sleep

# Chờ tối đa giữa hai lần thử; Retry-After của máy chủ dài hơn mốc này thì cũng chỉ chờ chừng này.
_MAX_RETRY_DELAY_SECONDS = 30.0

_ANSWER_SCHEMA: dict[str, Any] = AnalysisResponse.model_json_schema()

_BUDGET_EXHAUSTED = (
    "Bạn đã dùng hết số vòng truy xuất cho phép. Hãy nộp câu trả lời dựa trên bằng chứng đã có, "
    "và nói rõ trong `suggested_next_checks` những gì còn chưa kiểm được. Giữ đủ các điểm đã "
    "phân tích, kể cả điểm đi ngược lại kết luận, và để `verdict` khớp với các nhận định."
)

_SUBMIT_NOW = (
    "Hãy nộp câu trả lời cuối cùng theo đúng lược đồ, dựa trên phân tích ở trên. Mọi nhận định "
    "phải kèm Evidence ID đã lấy từ kết quả tool; không bịa ID. Giữ đủ các điểm đã phân tích, kể "
    "cả điểm đi ngược lại kết luận (mục đích sử dụng, ngữ cảnh làm giảm rủi ro): mỗi điểm là một "
    "nhận định riêng, và `verdict` phải khớp với toàn bộ các nhận định đó."
)

_CONTEXT_EXHAUSTED = (
    "\n\n[Đã cắt: ngân sách bằng chứng của phiên này đã hết, phần còn lại không được đưa vào. "
    "Hãy kết luận dựa trên phần đã có, và ghi phần chưa xem được vào `suggested_next_checks`.]"
)


class AgentLoop:
    """Chạy một lượt hỏi đáp, phát sự kiện tiến trình rồi phát câu trả lời."""

    def __init__(
        self,
        provider: LlmProvider,
        tool_client: EvidenceToolClient,
        settings: Settings,
        config: ExperimentConfig | None = None,
    ) -> None:
        self._provider = provider
        self._tools = tool_client
        self._max_iterations = settings.max_tool_iterations
        self._prompt_version = settings.prompt_version
        self._context_budget = settings.evidence_context_budget
        self._max_retries = settings.llm_max_retries
        # Cấu hình thực nghiệm (None = hỏi đáp thường); đóng dấu vào mọi trace của lượt này.
        self._config = config

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
        async for event in self._guarded(
            self._retrieve_then_submit(messages, traces, analysis_run_id), traces
        ):
            yield event

    async def run_experiment(
        self, analysis_run_id: str, focus_short_id: str
    ) -> AsyncIterator[AgentEvent]:
        """Một ô của thực nghiệm 3 cấu hình (docs/thuc-nghiem.md), theo ``config`` của loop.

        - ``evidence_agent``: đúng agent thật (cùng system prompt, cùng 5 tool), chỉ khác là câu
          hỏi là câu CHUẨN của thực nghiệm thay vì câu người dùng gõ.
        - ``llm_code`` / ``llm_static``: KHÔNG có tool. Agent-service tự lấy trước đoạn mã (và nội
          dung cảnh báo với ``llm_static``) rồi nộp đúng một lượt gọi model.
        """
        question = render("experiment_question.jinja2")
        if self._config in (None, "evidence_agent"):
            async for event in self.run(analysis_run_id, question, focus_short_id):
                yield event
            return
        traces: list[LlmTraceEntry] = []
        async for event in self._guarded(
            self._baseline(analysis_run_id, focus_short_id, question, traces), traces
        ):
            yield event

    async def _retrieve_then_submit(
        self, messages: list[Message], traces: list[LlmTraceEntry], analysis_run_id: str
    ) -> AsyncIterator[AgentEvent]:
        async for event in self._retrieve(messages, traces, analysis_run_id):
            yield event
        async for event in self._submit(messages, traces):
            yield event

    async def _baseline(
        self,
        analysis_run_id: str,
        focus_short_id: str,
        question: str,
        traces: list[LlmTraceEntry],
    ) -> AsyncIterator[AgentEvent]:
        """Cấu hình đối chứng: lấy trước bằng chứng cố định, rồi một lượt nộp duy nhất.

        Lần lấy trước KHÔNG ghi vào ``trace.tool_calls``: model không hề gọi tool, và số tool call
        của hai cấu hình này phải bằng 0 trong số liệu.
        """
        yield StatusEvent(iteration=0, message="Đang lấy mã nguồn")
        warning = await self._tools.call(
            "get_warning", {"evidence_id": focus_short_id}, analysis_run_id
        )
        if warning.error or not isinstance(warning.result, dict):
            yield ErrorEvent(
                message=f"khong lay duoc canh bao {focus_short_id}: {warning.error}",
                traces=traces,
            )
            return
        context = await self._tools.call(
            "get_code_context", {"evidence_id": focus_short_id}, analysis_run_id
        )
        items = (context.result or {}).get("items", []) if context.error is None else []
        warning_line = warning.result.get("start_line")
        contexts = [_numbered_context(item, warning_line) for item in items if item.get("snippet")]
        if not contexts:
            # Không có mã thì hai cấu hình này không có gì để đọc: báo hỏng thay vì để model
            # đoán, vì một verdict sinh từ đề bài rỗng sẽ đi thẳng vào số liệu.
            yield ErrorEvent(
                message=f"canh bao {focus_short_id} khong co ngu canh ma"
                + (f": {context.error}" if context.error else ""),
                traces=traces,
            )
            return

        messages = [
            Message(role="system", content=render("baseline_system.jinja2", config=self._config)),
            Message(
                role="user",
                content=render(
                    "baseline_user.jinja2",
                    config=self._config,
                    question=question,
                    warning=_warning_view(warning.result, focus_short_id),
                    contexts=contexts,
                ),
            ),
        ]
        async for event in self._submit(messages, traces):
            yield event

    async def _guarded(
        self, events: AsyncIterator[AgentEvent], traces: list[LlmTraceEntry]
    ) -> AsyncIterator[AgentEvent]:
        """Biến mọi lỗi giữa chừng thành :class:`ErrorEvent` mang đủ trace đã có."""
        try:
            async for event in events:
                yield event
        except _ModelCallFailedError as exc:
            # Lượt gọi hỏng cũng là một dòng llm_trace: thiếu nó thì "hỏng vì model quá tải"
            # không đếm được, và tỉ lệ lượt hỏi thất bại tuần 15 báo cáo thấp hơn thực tế.
            traces.append(exc.trace)
            logger.warning("goi model hong o vong %s: %s", exc.trace.iteration, exc)
            yield ErrorEvent(message=str(exc), traces=traces)
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

            called: list[tuple[Any, LlmTraceEntry]] = []
            async for event in self._call_model(messages, iteration, called, tools=ALL_TOOLS):
                yield event
            response, trace = called[0]
            traces.append(trace)

            if not response.tool_calls:
                # Model đã đủ thông tin. Giữ lại phần văn bản nó vừa viết: bước nộp phía sau
                # chỉ định dạng lại kết luận này, không suy luận lại từ đầu.
                if response.content:
                    messages.append(Message(role="assistant", content=response.content))
                    # Bước nộp KHÔNG được kết thúc bằng lượt của model: Gemini trả 400 "Requests
                    # ending with a model turn are not supported", Claude đời mới cũng từ chối
                    # (prefill). Gặp thật ở lần chạy Gemini đầu tiên — provider giả không lộ ra.
                    messages.append(Message(role="user", content=_SUBMIT_NOW))
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

    async def _submit(
        self, messages: list[Message], traces: list[LlmTraceEntry]
    ) -> AsyncIterator[AgentEvent]:
        """Một lượt gọi cuối, ép model nộp đúng cấu trúc :class:`AnalysisResponse`.

        Tách riêng khỏi vòng lặp vì ép định dạng và cho phép gọi tool loại trừ nhau: ép nộp
        nghĩa là buộc model gọi đúng một tool, nên nếu bật từ vòng đầu thì nó không bao giờ
        truy xuất được bằng chứng nào.
        """
        iteration = len(traces)
        called: list[tuple[Any, LlmTraceEntry]] = []
        async for event in self._call_model(
            messages, iteration, called, response_schema=_ANSWER_SCHEMA
        ):
            yield event
        response, trace = called[0]
        traces.append(trace)

        if not response.content:
            trace.error_message = "model khong nop duoc cau tra loi co cau truc"
            yield ErrorEvent(message=trace.error_message, traces=traces)
            return

        try:
            answer = AnalysisResponse.model_validate_json(response.content)
        except ValidationError as exc:
            # Đầu ra sai lược đồ là lỗi thật, không được chữa cháy bằng cách bịa một câu trả
            # lời rỗng: một verdict tự chế sẽ đi thẳng vào số liệu tuần 15.
            trace.error_message = f"phan hoi khong dung luoc do: {exc.error_count()} loi"
            logger.warning("phan hoi khong dung luoc do: %s", exc)
            yield ErrorEvent(message=trace.error_message, traces=traces)
            return

        yield AnswerEvent(answer=answer, traces=traces)

    async def _call_model(
        self,
        messages: list[Message],
        iteration: int,
        result: list[tuple[Any, LlmTraceEntry]],
        tools: list[dict[str, Any]] | None = None,
        response_schema: dict[str, Any] | None = None,
    ) -> AsyncIterator[AgentEvent]:
        """Gọi model, TỰ thử lại khi gặp lỗi tạm thời, và báo từng lần thử lại.

        Là async generator để phát được :class:`RetryEvent` giữa các lần thử; kết quả đặt vào
        ``result`` (generator không ``return`` giá trị được). ``latency_ms`` tính cả thời gian thử
        lại và chờ, giống số liệu cũ khi SDK tự thử lại ngầm.
        """
        started = time.monotonic()
        attempt = 0
        while True:
            try:
                response = await self._provider.chat(
                    messages, tools=tools, response_schema=response_schema
                )
                break
            except RetryableLlmError as exc:
                if attempt >= self._max_retries:
                    raise self._failed(
                        messages,
                        iteration,
                        started,
                        f"{exc.reason}; da thu lai {attempt} lan van hong, hay hoi lai sau it "
                        f"phut. Chi tiet: {exc}",
                    ) from exc
                attempt += 1
                delay = _retry_delay(attempt, exc.retry_after)
                logger.info(
                    "goi model loi tam thoi o vong %s (%s), thu lai lan %s/%s sau %.1fs",
                    iteration,
                    exc,
                    attempt,
                    self._max_retries,
                    delay,
                )
                yield RetryEvent(
                    iteration=iteration,
                    attempt=attempt,
                    max_attempts=self._max_retries,
                    reason=exc.reason,
                    delay_seconds=round(delay, 1),
                )
                await _sleep(delay)
            except Exception as exc:
                raise self._failed(messages, iteration, started, str(exc)) from exc
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
            config=self._config,
        )
        result.append((response, trace))

    def _failed(
        self, messages: list[Message], iteration: int, started: float, error: str
    ) -> "_ModelCallFailedError":
        return _ModelCallFailedError(
            LlmTraceEntry(
                iteration=iteration,
                provider=self._provider.provider_name,
                model=self._provider.model_name,
                prompt_version=self._prompt_version,
                latency_ms=int((time.monotonic() - started) * 1000),
                request_messages=[_trace_message(m) for m in messages],
                error_message=error,
                config=self._config,
            )
        )


def _retry_delay(attempt: int, retry_after: float | None) -> float:
    """Chờ bao lâu trước lần thử thứ ``attempt``: theo Retry-After nếu có, không thì lùi dần.

    Lùi dần 1, 2, 4, 8… giây (trần 30) kèm nhiễu ±25% để nhiều lượt hỏi cùng gặp 503 không dồn
    lại thử cùng một lúc.
    """
    if retry_after is not None and retry_after >= 0:
        return min(retry_after, _MAX_RETRY_DELAY_SECONDS)
    base = min(2.0 ** (attempt - 1), _MAX_RETRY_DELAY_SECONDS)
    return base * random.uniform(0.75, 1.25)


class _ModelCallFailedError(Exception):
    """Lượt gọi model hỏng, mang theo trace của chính lượt đó để vẫn ghi được vào llm_trace."""

    def __init__(self, trace: LlmTraceEntry) -> None:
        super().__init__(trace.error_message)
        self.trace = trace


def _numbered_context(item: dict[str, Any], warning_line: object) -> dict[str, Any]:
    """Một bản ghi CODE_CONTEXT thành các dòng có số, đánh dấu dòng bị cảnh báo.

    Đánh số theo ``start_line`` của bản ghi: snippet đã lưu bắt đầu đúng từ dòng đó. Dòng ghi chú
    cắt bớt mà core-backend thêm vào cuối (``// … (đã cắt bớt``) không mang số dòng.
    """
    start = item.get("start_line")
    lines: list[dict[str, Any]] = []
    number = start if isinstance(start, int) else None
    for text in str(item["snippet"]).split("\n"):
        if text.startswith("// … (đã cắt bớt"):
            lines.append({"number": "", "marked": False, "text": text})
            continue
        lines.append(
            {
                "number": number if number is not None else "",
                "marked": number is not None and number == warning_line,
                "text": text,
            }
        )
        if number is not None:
            number += 1
    return {"short_id": item.get("short_id"), "file_path": item.get("file_path"), "lines": lines}


def _warning_view(result: dict[str, Any], focus_short_id: str) -> dict[str, Any]:
    """Các trường của ``get_warning`` mà đề bài dùng — đặt tên lại cho template dễ đọc."""
    return {
        "short_id": result.get("short_id") or focus_short_id,
        "tool": result.get("tool"),
        "rule_id": result.get("rule_id"),
        "severity": result.get("severity_raw") or result.get("severity_normalized"),
        "file_path": result.get("file_path"),
        "start_line": result.get("start_line"),
        "summary": result.get("summary"),
    }


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
