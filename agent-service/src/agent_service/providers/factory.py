"""Chọn cài đặt :class:`LlmProvider` theo biến ``LLM_PROVIDER``.

Import SDK của từng hãng nằm BÊN TRONG nhánh tương ứng, không ở đầu file: ``pyproject.toml``
để mỗi SDK trong một extra riêng (``.[anthropic]``, ``.[openai]``), nên cài một cái vẫn chạy
được service. Import ở đầu file sẽ biến điều đó thành lời hứa suông.
"""

from agent_service.config import Settings
from agent_service.providers.base import LlmProvider


class ProviderNotAvailableError(RuntimeError):
    """Provider đã chọn chưa có cài đặt, hoặc thiếu gói phụ thuộc."""


def build_provider(settings: Settings) -> LlmProvider:
    """Dựng provider tương ứng, hỏng ngay lúc khởi động nếu không dựng được."""
    if settings.llm_provider == "anthropic":
        from agent_service.providers.anthropic_provider import AnthropicProvider

        try:
            return AnthropicProvider(settings)
        except ImportError as exc:  # pragma: no cover - phụ thuộc môi trường cài đặt
            raise ProviderNotAvailableError(
                "Thieu goi 'anthropic'. Cai bang: pip install -e '.[anthropic]'"
            ) from exc

    if settings.llm_provider == "openai":
        try:
            from agent_service.providers.openai_provider import OpenAICompatibleProvider

            return OpenAICompatibleProvider(settings)
        except ImportError as exc:  # pragma: no cover - phụ thuộc môi trường cài đặt
            raise ProviderNotAvailableError(
                "Thieu goi 'openai'. Cai bang: pip install -e '.[openai]'"
            ) from exc

    if settings.llm_provider == "scripted":
        from agent_service.providers.scripted import ScriptedProvider

        return ScriptedProvider.from_settings(settings)

    raise ProviderNotAvailableError(
        f"Provider '{settings.llm_provider}' chua co cai dat rieng. Voi Ollama, dung "
        "LLM_PROVIDER=openai va LLM_BASE_URL=http://<host>:11434/v1 (endpoint tuong thich OpenAI)."
    )
