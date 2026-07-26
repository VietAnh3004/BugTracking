"""Lược đồ phản hồi phải giữ được các bất biến mà thực nghiệm dựa vào.

Unsupported Claim Rate và Evidence Citation Accuracy được tính trực tiếp từ trường
``claims[].evidence_ids``. Nếu lược đồ âm thầm cho phép bỏ trường này, hoặc tự lọc bỏ claim
không có bằng chứng, thì số liệu trong báo cáo sẽ sai mà không có gì báo động.
"""

import pytest
from pydantic import ValidationError

from agent_service.schemas.analysis import AnalysisResponse, Claim, Verdict


def test_claim_khong_co_bang_chung_van_duoc_giu_lai() -> None:
    """Claim thiếu bằng chứng KHÔNG bị loại bỏ — nó là dữ liệu để tính unsupported claim rate."""
    claim = Claim(text="Cảnh báo này là lỗi thật", evidence_ids=[], confidence=0.9)

    assert claim.evidence_ids == []


def test_confidence_ngoai_khoang_bi_tu_choi() -> None:
    with pytest.raises(ValidationError):
        Claim(text="x", evidence_ids=["EV-1"], confidence=1.5)


def test_verdict_chi_nhan_ba_gia_tri() -> None:
    assert set(Verdict) == {
        Verdict.LIKELY_TRUE_POSITIVE,
        Verdict.LIKELY_FALSE_POSITIVE,
        Verdict.INSUFFICIENT_EVIDENCE,
    }


def test_json_schema_co_du_truong_cho_prompt() -> None:
    """Schema này được gửi cho LLM để ép định dạng đầu ra, nên phải đủ trường."""
    schema = AnalysisResponse.model_json_schema()
    claim_schema = schema["$defs"]["Claim"]["properties"]

    assert {"verdict", "summary", "claims"} <= set(schema["properties"])
    assert {"text", "evidence_ids", "confidence"} <= set(claim_schema)


def test_bang_chung_mau_thuan_mac_dinh_rong() -> None:
    """Không có bằng chứng phản bác là trạng thái hợp lệ, không phải lỗi."""
    response = AnalysisResponse(verdict=Verdict.INSUFFICIENT_EVIDENCE, summary="Chưa đủ dữ liệu")

    assert response.contradicting_evidence_ids == []
    assert response.claims == []
