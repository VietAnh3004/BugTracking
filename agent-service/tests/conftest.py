"""Đồ dùng chung cho test agent-service."""

from typing import Any

import pytest

from agent_service.config import Settings


@pytest.fixture
def settings() -> Settings:
    """Cấu hình tối thiểu, không đọc file .env của máy chạy."""
    # Truyền theo TÊN TRƯỜNG (chữ thường), không phải tên biến môi trường: pydantic-settings
    # chỉ ánh xạ tên viết hoa khi đọc từ môi trường, còn kwargs đi thẳng vào model.
    return Settings(
        _env_file=None,  # type: ignore[call-arg]
        core_api_url="http://core-backend:8080",  # type: ignore[arg-type]
        service_token="bi-mat-chia-se",
        llm_provider="scripted",
        llm_api_key="khong-dung-toi",
        llm_model="model-gia",
        max_tool_iterations=3,
    )


def items_result(count: int) -> dict[str, Any]:
    """Payload dạng ``ItemsResult`` của core-backend."""
    return {
        "items": [
            {
                "short_id": f"EV-{i:03X}A",
                "type": "TEST_RESULT",
                "summary": f"bang chung {i}",
                "file_path": "UserService.java",
                "start_line": 60 + i,
                "symbol": None,
                "relation": "SUPPORTS",
                "method": "STACK_FRAME",
                "weight": 1.0,
            }
            for i in range(count)
        ],
        "total": count,
    }
