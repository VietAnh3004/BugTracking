"""Cấu hình đọc từ biến môi trường.

Thiếu biến bắt buộc thì service không khởi động được — lỗi xuất hiện lúc deploy chứ không
phải giữa một phiên hội thoại của người dùng.
"""

from functools import lru_cache
from typing import Literal

from pydantic import Field, HttpUrl
from pydantic_settings import BaseSettings, SettingsConfigDict

LlmProviderName = Literal["anthropic", "openai", "ollama"]


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

    llm_provider: LlmProviderName = Field(..., description="Nhà cung cấp LLM đang dùng")
    llm_api_key: str = Field(..., min_length=1, description="Khoá API của provider")
    llm_model: str = Field(..., min_length=1, description="Định danh model")

    llm_temperature: float = Field(default=0.0, ge=0.0, le=2.0)
    llm_max_tokens: int = Field(default=4096, gt=0)

    # Chặn agent lặp vô hạn khi tool liên tục trả về kết quả rỗng.
    max_tool_iterations: int = Field(default=8, gt=0, le=30)
    # Số token bằng chứng tối đa nhồi vào một prompt, tránh làm loãng ngữ cảnh.
    evidence_context_budget: int = Field(default=12_000, gt=0)

    request_timeout_seconds: float = Field(default=120.0, gt=0)
    log_level: str = Field(default="INFO")


@lru_cache
def get_settings() -> Settings:
    """Trả về cấu hình đã nạp (cache để không đọc lại môi trường mỗi request)."""
    return Settings()  # type: ignore[call-arg]
