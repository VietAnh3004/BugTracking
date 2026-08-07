"""Nạp prompt từ file Jinja2 thay vì nhúng giữa code.

Hai lý do, cả hai đều là yêu cầu của đồ án chứ không phải sở thích: báo cáo cần trích dẫn prompt
chính xác, và thực nghiệm cần biết phiên bản prompt nào sinh ra kết quả nào (ghi vào
``llm_trace.prompt_version``).

``StrictUndefined`` có chủ đích: một biến gõ nhầm trong template phải nổ lúc render, chứ không
lặng lẽ render ra chuỗi rỗng rồi gửi một prompt thiếu vế lên model.
"""

from functools import lru_cache
from pathlib import Path
from typing import Any

from jinja2 import Environment, FileSystemLoader, StrictUndefined

PROMPT_DIR = Path(__file__).parent


@lru_cache
def _environment() -> Environment:
    return Environment(
        loader=FileSystemLoader(PROMPT_DIR),
        undefined=StrictUndefined,
        autoescape=False,
        trim_blocks=True,
        lstrip_blocks=True,
        keep_trailing_newline=True,
    )


def render(template_name: str, **context: Any) -> str:
    """Render một prompt. Ném :class:`jinja2.TemplateNotFound` nếu file không tồn tại."""
    return _environment().get_template(template_name).render(**context).strip()
