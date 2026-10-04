# WP-54: the AI service (D38). It reaches Ollama (IBM Granite) at OLLAMA_URL and needs ESG_AI_TOKEN.
#   docker build -f deploy/ai.Dockerfile -t atheris-esg-ai .
FROM python:3.11-slim
RUN useradd --system --create-home esg
WORKDIR /app
COPY esg-ai/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY esg-ai/app ./app
USER esg
EXPOSE 8090
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090", "--proxy-headers"]
