"""``stop_on_disconnect``: người gọi ngắt thì lời gọi đang chờ (thường là gọi LLM) bị huỷ NGAY.

Điều cần chứng minh không phải "luồng dừng" — Starlette cũng dừng được, chỉ là muộn — mà là
lời gọi ĐANG BAY bị huỷ trước khi nó tự trả về. Đó là chỗ tiết kiệm quota thật sự.
"""

import asyncio
from collections.abc import AsyncIterator

from agent_service.streaming import stop_on_disconnect


async def test_chuyen_tiep_du_su_kien_khi_khong_ai_ngat() -> None:
    async def events() -> AsyncIterator[int]:
        for i in range(3):
            yield i

    async def never() -> bool:
        return False

    received = [e async for e in stop_on_disconnect(events(), never, poll_seconds=0.01)]

    assert received == [0, 1, 2]


async def test_ngat_giua_chung_huy_loi_goi_dang_cho() -> None:
    state = {"llm_call_cancelled": False, "llm_call_finished": False, "disconnected": False}

    async def events() -> AsyncIterator[str]:
        yield "tool_call"
        try:
            # Giả lập một lượt gọi LLM dài — dài hơn hẳn thời gian test chờ.
            await asyncio.sleep(30)
            state["llm_call_finished"] = True
            yield "answer"
        except asyncio.CancelledError:
            state["llm_call_cancelled"] = True
            raise

    async def is_disconnected() -> bool:
        return state["disconnected"]

    received: list[str] = []
    async with asyncio.timeout(5):
        async for event in stop_on_disconnect(events(), is_disconnected, poll_seconds=0.01):
            received.append(event)
            # Người dùng bấm "Dừng" ngay sau sự kiện đầu tiên.
            state["disconnected"] = True

    assert received == ["tool_call"]
    assert state["llm_call_cancelled"] is True
    assert state["llm_call_finished"] is False


async def test_ngat_truoc_su_kien_dau_tien_khong_phat_gi() -> None:
    async def events() -> AsyncIterator[str]:
        await asyncio.sleep(30)
        yield "khong bao gio toi"

    async def already_gone() -> bool:
        return True

    async with asyncio.timeout(5):
        received = [e async for e in stop_on_disconnect(events(), already_gone, poll_seconds=0.01)]

    assert received == []
