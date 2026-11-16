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
