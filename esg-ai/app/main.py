"""Atheris ESG AI service (WP-50, D38; TRD §8.9): stateless, called only by the Spring backend.

No database access and no tenant data is kept: each request carries the data it needs and the answer goes back to the
backend, which stores it with the run's model and inputs (traceability) and asks a person to confirm before anything
is used.
"""
from __future__ import annotations

import hmac
import json
import os
import platform

import sklearn
import statsmodels
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import extract as extraction
from . import forecast as forecasting
from . import iforest
from .llm import LlmClient, LlmUnavailable, OllamaClient

VERSION = "1.0.0"
MAX_PDF_BYTES = 25 * 1024 * 1024


class Point(BaseModel):
    period: str = Field(max_length=7)
    value: float

class ForecastRequest(BaseModel):
    series: list[Point] = Field(max_length=600)
    frequency: str
    horizon: int
    level: float = 0.8

class Scored(BaseModel):
    id: str = Field(max_length=64)
    group: str = Field(max_length=200)
    seq: str = Field(default="", max_length=40)
    value: float

class IsolationRequest(BaseModel):
    points: list[Scored] = Field(max_length=50000)
    contamination: float = 0.05


def problem(status: int, code: str, detail: str) -> JSONResponse:
    return JSONResponse(status_code=status, media_type="application/problem+json",
                        content={"type": "about:blank", "title": code, "status": status, "detail": detail, "errorCode": code})


def create_app(llm: LlmClient | None = None, token: str | None = None) -> FastAPI:
    app = FastAPI(title="Atheris ESG AI service", version=VERSION)
    client = llm or OllamaClient(os.environ.get("OLLAMA_URL", "http://localhost:11434"), os.environ.get("OLLAMA_MODEL", "granite4:micro"))
    secret = token if token is not None else os.environ.get("ESG_AI_TOKEN", "")

    def authorised(x_ai_token: str = Header(default="")) -> None:
        if secret and not hmac.compare_digest(x_ai_token, secret):
            raise HTTPException(status_code=401, detail="missing or wrong service token")

    @app.exception_handler(HTTPException)
    async def http_error(_, e: HTTPException):
        return problem(e.status_code, "ESG-AI-" + str(e.status_code), str(e.detail))

    @app.exception_handler(RequestValidationError)
    async def invalid(_, e: RequestValidationError):
        first = e.errors()[0] if e.errors() else {}
        where = ".".join(str(x) for x in first.get("loc", [])[1:])
        return problem(422, "ESG-422-AI-INPUT", f"{where}: {first.get('msg', 'invalid request')}")

    @app.get("/v1/health")
    def health():
        return {"version": VERSION, "python": platform.python_version(), "forecasting": f"statsmodels {statsmodels.__version__}",
                "anomalies": f"scikit-learn {sklearn.__version__} IsolationForest", "llm": client.name, "llmAvailable": client.available()}

    @app.post("/v1/forecast", dependencies=[Depends(authorised)])
    def forecast(r: ForecastRequest):
        try:
            f = forecasting.forecast([p.model_dump() for p in r.series], r.frequency, r.horizon, r.level)
        except forecasting.ForecastError as e:
            return problem(422, "ESG-422-AI-INPUT", str(e))
        return {"method": f.method, "model": f.model, "n": f.n, "level": r.level, "points": f.points, "backtest": f.backtest,
                "warnings": f.warnings, "engine": f"statsmodels {statsmodels.__version__}"}

    @app.post("/v1/anomalies/isolation-forest", dependencies=[Depends(authorised)])
    def isolation(r: IsolationRequest):
        try:
            scores = iforest.score([p.model_dump() for p in r.points], r.contamination)
        except iforest.IsolationError as e:
            return problem(422, "ESG-422-AI-INPUT", str(e))
        return {"engine": f"scikit-learn {sklearn.__version__} IsolationForest(n_estimators=200, random_state=42)",
                "contamination": r.contamination, "scores": scores}

    @app.post("/v1/extract", dependencies=[Depends(authorised)])
    async def extract(file: UploadFile = File(...), metrics: str = Form(...)):
        pdf = await file.read(MAX_PDF_BYTES + 1)
        if len(pdf) > MAX_PDF_BYTES:
            return problem(413, "ESG-413-AI-FILE", "the PDF is larger than 25 MB")
        try:
            catalogue = json.loads(metrics)
            assert isinstance(catalogue, list) and all(isinstance(m, dict) and "key" in m for m in catalogue)
        except (ValueError, AssertionError):
            return problem(422, "ESG-422-AI-INPUT", "metrics must be a JSON list of {key, name, unit}")
        try:
            x = extraction.extract(pdf, catalogue[:200], client)
        except extraction.ExtractionError as e:
            return problem(422, "ESG-422-AI-INPUT", str(e))
        except LlmUnavailable as e:
            return problem(503, "ESG-503-AI-UNAVAILABLE", str(e))
        return {"model": client.name, "pagesTotal": x.pages_total, "pagesRead": x.pages_read, "values": x.values, "dropped": x.dropped}

    return app


app = create_app()
