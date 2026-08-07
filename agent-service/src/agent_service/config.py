"""Cấu hình đọc từ biến môi trường.

Thiếu biến bắt buộc thì service không khởi động được — lỗi xuất hiện lúc deploy chứ không
phải giữa một phiên hội thoại của người dùng.
"""

from functools import lru_cache
from typing import Literal

from pydantic import Field, HttpUrl
from pydantic_settings import BaseSettings, SettingsConfigDict

# "scripted" là provider giả trả kịch bản định sẵn. Nó nằm trong danh sách hợp lệ có chủ đích:
# nhờ vậy nghiệm thu end-to-end chạy được toàn bộ đường đi frontend → core-backend →
# agent-service mà không cần khoá API thật, và test không bao giờ gọi LLM thật.
LlmProviderName = Literal["anthropic", "openai", "ollama", "scripted"]


class Settings(BaseSettings):
    """Biến môi trường của agent-service."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # Agent không truy cập cơ sở dữ liệu trực tiếp; mọi bằng chứng lấy qua Evidence Tool API
    # của core-backend. Ranh giới này khiến từng lượt truy xuất đều quan sát và đo được.
    core_api_url: HttpUrl = Field(..., description="URL nội bộ tới core-backend")
    # Bí mật chia sẻ cho header X-Service-Token. KHÔNG phải JWT người dùng: agent-service gọi
    # server-to-server và không bao giờ nhìn thấy token của người đăng nhập (ADR 0005 điểm 2).
    service_token: str = Field(..., min_length=1, description="Bí mật gọi /api/v1/tools/**")

    llm_provider: LlmProviderName = Field(..., description="Nhà cung cấp LLM đang dùng")
    llm_api_key: str = Field(..., min_length=1, description="Khoá API của provider")
    llm_model: str = Field(..., min_length=1, description="Định danh model")

    # KHÔNG mặc định 0.0. Họ model Claude 5 (và Opus 4.7 trở đi) TỪ CHỐI tham số này bằng lỗi
    # 400 — đặt sẵn một giá trị nghĩa là mọi request đều hỏng ngay khi bật provider thật. Để
    # None nghĩa là "không gửi", và tính ổn định giữa các lần chạy được báo cáo bằng cách chạy
    # lặp rồi đo phương sai (kế hoạch §6) chứ không bằng một tham số mà API không còn nhận.
    llm_temperature: float | None = Field(default=None, ge=0.0, le=2.0)
    llm_max_tokens: int = Field(default=8192, gt=0)

    # Chặn agent lặp vô hạn khi tool liên tục trả về kết quả rỗng.
    max_tool_iterations: int = Field(default=8, gt=0, le=30)
    # Số ký tự bằng chứng tối đa nhồi vào một prompt, tránh làm loãng ngữ cảnh.
    evidence_context_budget: int = Field(default=12_000, gt=0)

    # Phiên bản prompt đang dùng, ghi vào llm_trace. Thực nghiệm cần biết prompt nào sinh ra
    # kết quả nào; không có cột này thì kết quả tuần 15 không tái lập được.
    prompt_version: str = Field(default="v1", min_length=1)

    request_timeout_seconds: float = Field(default=120.0, gt=0)
    log_level: str = Field(default="INFO")


@lru_cache
def get_settings() -> Settings:
    """Trả về cấu hình đã nạp (cache để không đọc lại môi trường mỗi request)."""
    return Settings()  # type: ignore[call-arg]
