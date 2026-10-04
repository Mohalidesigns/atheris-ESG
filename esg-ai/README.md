# esg-ai — Atheris ESG AI service (WP-50, D38)

Stateless FastAPI service called only by the Spring backend (`HttpAiEngine`), with a shared token (`X-AI-Token`).
It keeps no data. Endpoints: `GET /v1/health`, `POST /v1/forecast`, `POST /v1/anomalies/isolation-forest`,
`POST /v1/extract` (multipart: `file` PDF + `metrics` JSON). Errors are problem+json (`ESG-422-AI-INPUT`,
`ESG-503-AI-UNAVAILABLE`, `ESG-413-AI-FILE`).

```bash
python3.11 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest -q
ESG_AI_TOKEN=local-dev-ai-token .venv/bin/uvicorn app.main:app --port 8090
```

Extraction needs Ollama with an IBM Granite model (D12): `ollama pull granite4:micro`. Settings: `OLLAMA_URL`
(default `http://localhost:11434`), `OLLAMA_MODEL` (default `granite4:micro`), `ESG_AI_TOKEN` (required outside local).
