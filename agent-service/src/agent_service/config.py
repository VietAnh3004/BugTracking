"""Cấu hình đọc từ biến môi trường.

Thiếu biến bắt buộc thì service không khởi động được — lỗi xuất hiện lúc deploy chứ không
phải giữa một phiên hội thoại của người dùng.
"""

from functools import lru_cache
from typing import Literal

from pydantic import Field, HttpUrl, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# "scripted" là provider giả trả kịch bản định sẵn. Nó nằm trong danh sách hợp lệ có chủ đích:
# nhờ vậy nghiệm thu end-to-end chạy được toàn bộ đường đi frontend → core-backend →
# agent-service mà không cần khoá API thật, và test không bao giờ gọi LLM thật.
LlmProviderName = Literal["anthropic", "openai", "ollama", "scripted"]
EmbeddingProviderName = Literal["none", "gemini"]


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
    # Chỉ provider "openai" đọc: trỏ client tương thích OpenAI sang Gemini, Groq, OpenRouter,
    # Ollama… Bỏ trống thì gọi chính OpenAI. Chuỗi rỗng = bỏ trống, vì docker-compose truyền
    # biến chưa đặt dưới dạng "" chứ không phải vắng mặt.
    llm_base_url: str | None = Field(default=None, description="Base URL API tương thích OpenAI")

    # Model "Premium" cho dự án được admin nâng hạng. Cùng định dạng với LLM_*; thiếu API key thì
    # tier premium TẮT (không âm thầm rơi về model free — xem ``premium_enabled``). Chuỗi rỗng =
    # chưa đặt, vì docker-compose truyền biến chưa đặt dưới dạng "".
    premium_llm_provider: LlmProviderName = Field(default="anthropic")
    premium_llm_api_key: str | None = Field(default=None)
    premium_llm_model: str = Field(default="claude-sonnet-5", min_length=1)
    premium_llm_base_url: str | None = Field(default=None)

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
    # Số lần SDK tự thử lại khi gặp 429/5xx/mất kết nối, giãn cách tăng dần (tối đa ~8 giây).
    # Mặc định 2 = mặc định của SDK. Gói free (Gemini…) hay trả 503 "high demand" theo đợt:
    # 2 lần cách nhau dưới 1 giây là quá ngắn, một lượt hỏi 5-9 vòng gọi gần như chắc hỏng.
    llm_max_retries: int = Field(default=2, ge=0, le=10)
    log_level: str = Field(default="INFO")

    # Embedding cho search_evidence (tìm theo nghĩa). "none" = tắt: core-backend vẫn tìm bằng so
    # chữ, và ghi rõ lý do vào lần phân tích để người dùng thấy — không hỏng im lặng.
    embedding_provider: EmbeddingProviderName = Field(default="none")
    embedding_model: str = Field(default="gemini-embedding-001", min_length=1)
    # 768 chứ không 3072 mặc định của Gemini: pgvector chỉ dựng được index HNSW tới 2000 chiều,
    # và Gemini khuyến nghị 768 là mức cân bằng chất lượng/dung lượng.
    embedding_dimensions: int = Field(default=768, gt=0, le=3072)
    # Bỏ trống thì dùng LLM_API_KEY — trường hợp thường gặp là cùng một key Gemini cho cả hai.
    embedding_api_key: str | None = Field(default=None)
    embedding_base_url: str = Field(default="https://generativelanguage.googleapis.com/v1beta")

    @field_validator("embedding_api_key", mode="before")
    @classmethod
    def _blank_embedding_key_is_unset(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip():
            return None
        return value

    @property
    def effective_embedding_api_key(self) -> str:
        """Key cho embedding: EMBEDDING_API_KEY nếu có, không thì dùng chung LLM_API_KEY."""
        return self.embedding_api_key or self.llm_api_key

    @field_validator("premium_llm_api_key", "premium_llm_base_url", mode="before")
    @classmethod
    def _blank_premium_is_unset(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip():
            return None
        return value

    @property
    def premium_enabled(self) -> bool:
        """Tier premium dùng được khi đã có khoá API cho nó."""
        return bool(self.premium_llm_api_key)

    def premium_view(self) -> "Settings":
        """Bản sao cấu hình với ``llm_*`` thay bằng ``premium_*``, để dựng provider premium.

        Provider chỉ đọc ``settings.llm_*``; đổi nguồn ở ĐÂY thì không provider nào phải biết có
        hai tier. ``llm_temperature`` bị bỏ khi premium là Anthropic vì họ Claude 5 trả 400 cho
        tham số này, còn cấu hình free (Gemini) có thể đang đặt nó.
        """
        if not self.premium_enabled:
            raise ValueError("PREMIUM_LLM_API_KEY chua dat")
        return self.model_copy(
            update={
                "llm_provider": self.premium_llm_provider,
                "llm_api_key": self.premium_llm_api_key,
                "llm_model": self.premium_llm_model,
                "llm_base_url": self.premium_llm_base_url,
                "llm_temperature": None
                if self.premium_llm_provider == "anthropic"
                else self.llm_temperature,
            }
        )

    @field_validator("llm_base_url", mode="before")
    @classmethod
    def _blank_base_url_is_unset(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip():
            return None
        return value


@lru_cache
def get_settings() -> Settings:
    """Trả về cấu hình đã nạp (cache để không đọc lại môi trường mỗi request)."""
    return Settings()  # type: ignore[call-arg]
