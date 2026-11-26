"""Cấu hình phải hỏng ngay lúc khởi động khi thiếu hoặc sai biến môi trường.

Đây không phải test cho lấy lệ: cả ba service đều dựa vào tính chất fail-fast này, và nếu nó
lặng lẽ mất đi thì một biến gõ nhầm sẽ chỉ lộ ra giữa lúc người dùng đang dùng.
"""

import pytest
from pydantic import ValidationError

from agent_service.config import Settings

REQUIRED_ENV = {
    "CORE_API_URL": "http://localhost:8080",
    "SERVICE_TOKEN": "bi-mat-chia-se",
    "LLM_PROVIDER": "anthropic",
    "LLM_API_KEY": "test-key",
    "LLM_MODEL": "claude-sonnet-5",
}

OPTIONAL_ENV = (
    "LLM_TEMPERATURE",
    "MAX_TOOL_ITERATIONS",
    "PROMPT_VERSION",
    "LLM_MAX_TOKENS",
    "PREMIUM_LLM_PROVIDER",
    "PREMIUM_LLM_API_KEY",
    "PREMIUM_LLM_MODEL",
    "PREMIUM_LLM_BASE_URL",
    "EMBEDDING_RETRY_MAX_WAIT_SECONDS",
    "EMBEDDING_QUERY_MAX_WAIT_SECONDS",
)


@pytest.fixture(autouse=True)
def clean_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """Xoá mọi biến liên quan để test không phụ thuộc môi trường của máy chạy.

    Việc bỏ qua file .env do ``Settings(_env_file=None)`` ở từng test đảm nhiệm.
    """
    for name in (*REQUIRED_ENV, *OPTIONAL_ENV):
        monkeypatch.delenv(name, raising=False)


def test_nap_duoc_khi_du_bien(monkeypatch: pytest.MonkeyPatch) -> None:
    for name, value in REQUIRED_ENV.items():
        monkeypatch.setenv(name, value)

    settings = Settings(_env_file=None)  # type: ignore[call-arg]

    assert settings.llm_provider == "anthropic"
    assert settings.llm_model == "claude-sonnet-5"
    assert settings.prompt_version == "v2"


def test_ngan_sach_cho_han_muc_embedding_co_mac_dinh_va_phai_duong(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    for name, value in REQUIRED_ENV.items():
        monkeypatch.setenv(name, value)

    settings = Settings(_env_file=None)  # type: ignore[call-arg]
    assert settings.embedding_retry_max_wait_seconds == 150
    assert settings.embedding_query_max_wait_seconds == 10

    monkeypatch.setenv("EMBEDDING_RETRY_MAX_WAIT_SECONDS", "0")
    with pytest.raises(ValidationError):
        Settings(_env_file=None)  # type: ignore[call-arg]


def test_temperature_mac_dinh_la_khong_gui(monkeypatch: pytest.MonkeyPatch) -> None:
    """Mặc định phải là None, KHÔNG phải 0.0.

    Các model từ Claude Opus 4.7 trở đi trả lời 400 cho ``temperature``. Một giá trị mặc định
    khác None nghĩa là mọi request đều hỏng ngay khi bật provider thật — đúng loại lỗi chỉ lộ
    ra lúc chạy mà cấu hình fail-fast sinh ra để chặn.
    """
    for name, value in REQUIRED_ENV.items():
        monkeypatch.setenv(name, value)

    assert Settings(_env_file=None).llm_temperature is None  # type: ignore[call-arg]


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


def _load(monkeypatch: pytest.MonkeyPatch, **extra: str) -> Settings:
    for name, value in {**REQUIRED_ENV, **extra}.items():
        monkeypatch.setenv(name, value)
    return Settings(_env_file=None)  # type: ignore[call-arg]


def test_premium_tat_khi_khong_co_khoa(monkeypatch: pytest.MonkeyPatch) -> None:
    assert _load(monkeypatch).premium_enabled is False
    # docker-compose truyen bien chua dat duoi dang chuoi rong.
    assert _load(monkeypatch, PREMIUM_LLM_API_KEY="  ").premium_enabled is False


def test_premium_view_thay_llm_bang_premium(monkeypatch: pytest.MonkeyPatch) -> None:
    settings = _load(
        monkeypatch,
        LLM_PROVIDER="openai",
        LLM_MODEL="gemini-3.5-flash-lite",
        LLM_BASE_URL="https://example.test/v1/",
        LLM_TEMPERATURE="0.3",
        PREMIUM_LLM_API_KEY="khoa-claude",
    )

    premium = settings.premium_view()

    assert premium.llm_provider == "anthropic"
    assert premium.llm_model == "claude-sonnet-5"
    assert premium.llm_api_key == "khoa-claude"
    assert premium.llm_base_url is None, "base url cua Gemini khong duoc ro sang Claude"
    assert premium.llm_temperature is None, "Claude 5 tra 400 cho temperature"
    # Ban goc khong bi doi.
    assert settings.llm_model == "gemini-3.5-flash-lite"
    assert settings.llm_temperature == 0.3


def test_premium_view_khi_chua_bat_thi_hong_ro_rang(monkeypatch: pytest.MonkeyPatch) -> None:
    with pytest.raises(ValueError, match="PREMIUM_LLM_API_KEY"):
        _load(monkeypatch).premium_view()
