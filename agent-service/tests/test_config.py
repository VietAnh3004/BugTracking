"""Cấu hình phải hỏng ngay lúc khởi động khi thiếu hoặc sai biến môi trường.

Đây không phải test cho lấy lệ: cả ba service đều dựa vào tính chất fail-fast này, và nếu nó
lặng lẽ mất đi thì một biến gõ nhầm sẽ chỉ lộ ra giữa lúc người dùng đang dùng.
"""

import pytest
from pydantic import ValidationError

from agent_service.config import Settings

REQUIRED_ENV = {
    "CORE_API_URL": "http://localhost:8080",
    "LLM_PROVIDER": "anthropic",
    "LLM_API_KEY": "test-key",
    "LLM_MODEL": "claude-sonnet-5",
}


@pytest.fixture(autouse=True)
def clean_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """Xoá mọi biến liên quan để test không phụ thuộc môi trường của máy chạy.

    Việc bỏ qua file .env do ``Settings(_env_file=None)`` ở từng test đảm nhiệm.
    """
    for name in (*REQUIRED_ENV, "LLM_TEMPERATURE", "MAX_TOOL_ITERATIONS"):
        monkeypatch.delenv(name, raising=False)


def test_nap_duoc_khi_du_bien(monkeypatch: pytest.MonkeyPatch) -> None:
    for name, value in REQUIRED_ENV.items():
        monkeypatch.setenv(name, value)

    settings = Settings(_env_file=None)  # type: ignore[call-arg]

    assert settings.llm_provider == "anthropic"
    assert settings.llm_model == "claude-sonnet-5"
    # Mặc định temperature là 0 để kết quả thực nghiệm bớt dao động giữa các lần chạy.
    assert settings.llm_temperature == 0.0


@pytest.mark.parametrize("missing", sorted(REQUIRED_ENV))
def test_thieu_bien_bat_buoc_thi_hong(monkeypatch: pytest.MonkeyPatch, missing: str) -> None:
    for name, value in REQUIRED_ENV.items():
        if name != missing:
            monkeypatch.setenv(name, value)

    with pytest.raises(ValidationError) as exc:
        Settings(_env_file=None)  # type: ignore[call-arg]

    assert missing.lower() in str(exc.value).lower()


def test_provider_khong_hop_le_thi_hong(monkeypatch: pytest.MonkeyPatch) -> None:
    for name, value in REQUIRED_ENV.items():
        monkeypatch.setenv(name, value)
    monkeypatch.setenv("LLM_PROVIDER", "gpt-tu-che")

    with pytest.raises(ValidationError):
        Settings(_env_file=None)  # type: ignore[call-arg]


def test_so_vong_goi_tool_bi_chan_tren(monkeypatch: pytest.MonkeyPatch) -> None:
    """Chặn agent lặp vô hạn khi tool liên tục trả về kết quả rỗng."""
    for name, value in REQUIRED_ENV.items():
        monkeypatch.setenv(name, value)
    monkeypatch.setenv("MAX_TOOL_ITERATIONS", "500")

    with pytest.raises(ValidationError):
        Settings(_env_file=None)  # type: ignore[call-arg]
