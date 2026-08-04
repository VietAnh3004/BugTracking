FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Cài dependency trước source để lớp cache không bị vô hiệu mỗi lần sửa code.
#
# KHÔNG copy README.md: `pyproject.toml` không khai trường `readme` nên hatchling không cần tới
# nó, và tài liệu không có việc gì trong image chạy. Trước đây có copy, nên khi `.dockerignore`
# loại README.md thì build chết ngay ở bước này với "/README.md: not found".
COPY pyproject.toml ./
COPY src ./src
RUN pip install --no-cache-dir ".[anthropic,openai]"

RUN useradd --create-home --uid 1000 app
USER app

EXPOSE 8000
CMD ["uvicorn", "agent_service.main:app", "--host", "0.0.0.0", "--port", "8000"]
