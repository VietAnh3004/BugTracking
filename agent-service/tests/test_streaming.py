"""``stop_on_disconnect``: người gọi ngắt thì lời gọi đang chờ (thường là gọi LLM) bị huỷ NGAY.

Điều cần chứng minh không phải "luồng dừng" — Starlette cũng dừng được, chỉ là muộn — mà là
lời gọi ĐANG BAY bị huỷ trước khi nó tự trả về. Đó là chỗ tiết kiệm quota thật sự.
"""

import asyncio
from collections.abc import AsyncIterator

from agent_service.streaming import SSE_HEARTBEAT, stop_on_disconnect, with_heartbeat


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


async def test_heartbeat_chen_nhip_khi_im_lang_va_khong_mat_su_kien() -> None:
    """Lời gọi dài hơn ``interval`` thì có nhịp chen giữa; sự kiện sau vẫn tới đủ, đúng thứ tự."""

    async def events() -> AsyncIterator[str]:
        yield "status"
        await asyncio.sleep(0.35)  # giả lập một lời gọi LLM dài
        yield "answer"

    received = [e async for e in with_heartbeat(events(), interval=0.1)]

    assert received[0] == "status"
    assert received[-1] == "answer"
    beats = received[1:-1]
    assert beats, "phai co it nhat mot nhip trong luc im lang"
    assert all(beat == SSE_HEARTBEAT for beat in beats)


async def test_heartbeat_khong_chen_khi_su_kien_toi_deu() -> None:
    async def events() -> AsyncIterator[str]:
        for name in ("status", "tool_call", "answer"):
            yield name

    received = [e async for e in with_heartbeat(events(), interval=5)]

    assert received == ["status", "tool_call", "answer"]


async def test_heartbeat_ngat_giua_chung_van_huy_loi_goi_dang_cho() -> None:
    """Bọc trong ``stop_on_disconnect`` như ``/analyze``: bấm Dừng vẫn huỷ ngay lời gọi đang bay."""
    state = {"cancelled": False, "disconnected": False}

    async def events() -> AsyncIterator[str]:
        yield "tool_call"
        try:
            await asyncio.sleep(30)
            yield "answer"
        except asyncio.CancelledError:
            state["cancelled"] = True
            raise

    async def is_disconnected() -> bool:
        return state["disconnected"]

    received: list[str] = []
    async for event in stop_on_disconnect(
        with_heartbeat(events(), interval=0.05), is_disconnected, poll_seconds=0.01
    ):
        received.append(event)
        if len(received) >= 3:
            state["disconnected"] = True

    assert received[0] == "tool_call"
    assert "answer" not in received
    assert state["cancelled"], "loi goi dang bay phai bi huy, khong doi no tu tra ve"
