# Base image is overridable (e.g. a registry mirror when Docker Hub rate-limits):
#   docker compose build --build-arg PYTHON_IMAGE=mirror.gcr.io/library/python:3.13-slim
ARG PYTHON_IMAGE=python:3.13-slim
FROM ${PYTHON_IMAGE} AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /app

COPY pyproject.toml ./
COPY app ./app
COPY workers ./workers
RUN pip install ".[fcm]"

COPY alembic.ini ./
COPY alembic ./alembic
COPY scripts ./scripts

RUN useradd --system --uid 10001 dealmate
USER dealmate
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4).status == 200 else 1)"
# Migrations run on start: this is a single-instance, single-family deployment.
CMD ["sh", "-c", "alembic upgrade head && exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --proxy-headers"]
