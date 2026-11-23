"""Dừng một luồng sự kiện ngay khi người gọi ngắt kết nối.

Vì sao cần, dù Starlette đã tự xử lý ngắt kết nối: Starlette chỉ phát hiện client đã đi khi nó
GHI khung tiếp theo. Mà giữa hai khung là một lượt gọi LLM có thể kéo dài hàng chục giây — suốt
khoảng đó agent vẫn gọi model, vẫn tốn quota cho một câu trả lời không ai nhận. Người dùng bấm
"Dừng" thì lượt gọi đang bay phải bị huỷ NGAY, không phải đợi nó trả về.

Cơ chế: chạy song song việc lấy sự kiện kế tiếp và việc canh ngắt kết nối; cái nào xong trước
thắng. Ngắt kết nối thắng thì huỷ task đang chờ sự kiện — ``CancelledError`` rơi đúng vào chỗ
``await provider.chat(...)``, và httpx huỷ request HTTP tới nhà cung cấp LLM.
"""

from __future__ import annotations

import asyncio
import contextlib
import logging
from collections.abc import AsyncIterator, Awaitable, Callable

logger = logging.getLogger(__name__)

# Đủ nhanh để người dùng không thấy độ trễ khi bấm Dừng, đủ thưa để không đáng kể về CPU.
DEFAULT_POLL_SECONDS = 0.5

# Nhịp giữ kết nối. core-backend cắt lượt hỏi khi im lặng quá AGENT_IDLE_TIMEOUT (mặc định 90 giây),
# nên nhịp phải dày hơn hẳn mốc đó; 15 giây cũng dưới mọi timeout đọc của proxy thường gặp.
HEARTBEAT_SECONDS = 15.0

# Dòng chú thích SSE: đặc tả bắt client bỏ qua dòng bắt đầu bằng ``:``, nên không phải sự kiện nào.
SSE_HEARTBEAT = ": ping\n\n"


async def with_heartbeat(
    events: AsyncIterator[str],
    interval: float = HEARTBEAT_SECONDS,
    beat: str = SSE_HEARTBEAT,
) -> AsyncIterator[str]:
    """Chuyển tiếp ``events``, chen ``beat`` mỗi khi im lặng quá ``interval`` giây.

    Vì sao cần (sửa 23/11/2026): timeout cũ của core-backend tính trên TỔNG cả lượt và cắt ba lượt
    Gemini khoẻ mạnh nhưng chậm. Đổi sang tính theo thời gian IM LẶNG thì phải có gì đó đi trên dây
    cả khi đang chờ một lời gọi LLM dài — một lời gọi có thể im tới ``request_timeout_seconds`` nhân
    số lần thử lại.

    KHÔNG huỷ lời gọi đang chờ khi phát nhịp: chỉ chờ tiếp chính task đó. Bị huỷ từ ngoài (người
    dùng bấm Dừng, qua ``stop_on_disconnect``) thì huỷ luôn task đang chờ.
    """
    iterator = aiter(events)
    pending: asyncio.Future[str] | None = None
    try:
        while True:
            if pending is None:
                pending = asyncio.ensure_future(anext(iterator))
            done, _ = await asyncio.wait({pending}, timeout=interval)
            if not done:
                yield beat
                continue
            finished, pending = pending, None
            try:
                item = finished.result()
            except StopAsyncIteration:
                return
            yield item
    finally:
        if pending is not None:
            pending.cancel()
            with contextlib.suppress(asyncio.CancelledError, StopAsyncIteration):
                await pending
        aclose = getattr(iterator, "aclose", None)
        if aclose is not None:
            with contextlib.suppress(RuntimeError, StopAsyncIteration):
                await aclose()


async def stop_on_disconnect[T](
    events: AsyncIterator[T],
    is_disconnected: Callable[[], Awaitable[bool]],
    poll_seconds: float = DEFAULT_POLL_SECONDS,
) -> AsyncIterator[T]:
    """Chuyển tiếp ``events`` cho tới khi ``is_disconnected()`` trả True, rồi huỷ phần còn lại."""

    async def wait_disconnect() -> None:
        while not await is_disconnected():
            await asyncio.sleep(poll_seconds)

    iterator = aiter(events)
    watcher = asyncio.ensure_future(wait_disconnect())
    try:
        while True:
            next_event = asyncio.ensure_future(anext(iterator))
            done, _ = await asyncio.wait({next_event, watcher}, return_when=asyncio.FIRST_COMPLETED)
            if next_event not in done:
                # Ngắt kết nối thắng: huỷ đúng lời gọi đang chờ (thường là gọi LLM).
                next_event.cancel()
                with contextlib.suppress(asyncio.CancelledError, StopAsyncIteration):
                    await next_event
                logger.info("nguoi goi ngat ket noi giua luot; da huy luot dang chay")
                return
            try:
                event = next_event.result()
            except StopAsyncIteration:
                return
            yield event
    finally:
        watcher.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await watcher
        # Đóng generator gốc để khối `finally` bên trong nó (nếu có) chạy ngay, không đợi GC.
        aclose = getattr(iterator, "aclose", None)
        if aclose is not None:
            with contextlib.suppress(RuntimeError, StopAsyncIteration):
                await aclose()
