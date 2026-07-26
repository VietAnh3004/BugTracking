FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Cài dependency trước source để lớp cache không bị vô hiệu mỗi lần sửa code.
COPY pyproject.toml README.md ./
COPY src ./src
RUN pip install --no-cache-dir ".[anthropic,openai]"

RUN useradd --create-home --uid 1000 app
USER app

EXPOSE 8000
CMD ["uvicorn", "agent_service.main:app", "--host", "0.0.0.0", "--port", "8000"]
