"""Prompt là MÃ NGUỒN của đồ án này, không phải văn bản trang trí.

Ba luận điểm dưới đây nằm trong ``system.jinja2`` và không có ở chỗ nào khác. Xoá một trong ba đi
thì mô hình vẫn chạy, vẫn trả về đúng lược đồ, vẫn trông bình thường — chỉ là kết luận sai theo
đúng cách mà case study dựng lên để bắt. Không test nào khác đỏ.

Test này cố ý bám vào **nội dung**, chấp nhận phải sửa khi diễn đạt đổi. Đó là cái giá phải trả
để việc xoá mất một luận điểm không đi qua code review trong im lặng.
"""

import pytest

from agent_service.prompts.loader import render
from agent_service.tools.schemas import ALL_TOOLS


@pytest.fixture
def system_prompt() -> str:
    return render("system.jinja2")


def test_ep_trich_dan_bang_evidence_id(system_prompt: str) -> None:
    """Không có yêu cầu này thì mọi nhận định đều bị hạ cấp và cả hệ thống vô dụng."""
    assert "Evidence ID" in system_prompt
    assert "EV-XXXX" in system_prompt


def test_cam_bia_evidence_id(system_prompt: str) -> None:
    assert "đừng đoán" in system_prompt.lower() or "không được bịa" in system_prompt.lower()


def test_canh_bao_ve_cai_bay_contradicts(system_prompt: str) -> None:
    """Luận điểm quan trọng nhất của cả prompt.

    ADR 0009 đo được: `CONTRADICTS` nhiều gấp hơn 6 lần `SUPPORTS` trên vulnshop (52 so với 8),
    vì mỗi test xanh trong lớp test khớp đều sinh một liên kết. Một mô hình đọc 52 dòng "test
    xanh phủ dòng này" mà không được cảnh báo sẽ kết luận `LIKELY_FALSE_POSITIVE` — rơi đúng cái
    bẫy `findByUsername` mà case study cố tình gài.
    """
    assert "`CONTRADICTS` không chứng minh cảnh báo sai" in system_prompt
    # Số lượng liên kết không phải bằng chứng: mười test xanh cùng đi qua một dòng vẫn là một
    # loại bằng chứng yếu.
    assert "cũng không có ý nghĩa" in system_prompt
    # Muốn kết luận báo nhầm thì phải nêu lý do TRONG MÃ, không chỉ dựa vào test xanh.
    assert "get_code_context" in system_prompt


def test_phan_biet_bang_chung_manh_va_yeu(system_prompt: str) -> None:
    """Che `method`/`weight` đi thì mọi bằng chứng trông như nhau (ADR 0009 quyết định 1)."""
    assert "STACK_FRAME" in system_prompt
    assert "NAMING_CONVENTION" in system_prompt


def test_ket_qua_rong_la_mot_ket_luan(system_prompt: str) -> None:
    """Phân biệt "đã tra và không thấy" với "chưa tra".

    Đó là điều kiện để `INSUFFICIENT_EVIDENCE` có nghĩa.
    """
    assert "mảng rỗng" in system_prompt


def test_ba_verdict_deu_duoc_neu_ten(system_prompt: str) -> None:
    for verdict in ("LIKELY_TRUE_POSITIVE", "LIKELY_FALSE_POSITIVE", "INSUFFICIENT_EVIDENCE"):
        assert verdict in system_prompt


def test_moi_tool_deu_noi_ro_khi_nao_nen_goi() -> None:
    """Thiếu vế "tool này không trả về gì" thì model gọi lại nhiều lần để tìm thứ không có.

    Đây là cách rẻ nhất để cắt số vòng lặp thừa, và số vòng lặp đi thẳng vào hoá đơn.
    """
    for tool in ALL_TOOLS:
        mo_ta = tool["description"]
        assert len(mo_ta) > 120, f"{tool['name']}: mô tả quá ngắn để model quyết định gọi hay không"
        assert "Gọi" in mo_ta or "gọi" in mo_ta, f"{tool['name']}: không nói KHI NÀO nên gọi"


def test_get_related_tests_canh_bao_ngay_trong_mo_ta_tool() -> None:
    """Cảnh báo phải có ở CẢ mô tả tool, không chỉ ở system prompt.

    Mô tả tool là thứ model đọc ngay lúc quyết định gọi và ngay lúc đọc kết quả; system prompt ở
    xa hơn trong ngữ cảnh. Với một luận điểm dễ hiểu sai đến thế, nhắc hai lần là đúng liều.
    """
    mo_ta = next(t for t in ALL_TOOLS if t["name"] == "get_related_tests")["description"]

    assert "CONTRADICTS" in mo_ta
    assert "KHÔNG chứng minh cảnh báo sai" in mo_ta


def test_khong_tool_nao_khai_analysis_run_id() -> None:
    """Phạm vi tra cứu bằng chứng KHÔNG phải thứ model được chọn (ADR 0010 quyết định 4).

    Nó quyết định một Evidence ID có nghĩa hay không; để model tự điền là để nó tự chọn nơi trích
    dẫn của mình được coi là hợp lệ.
    """
    for tool in ALL_TOOLS:
        assert "analysis_run_id" not in tool["input_schema"]["properties"], tool["name"]
