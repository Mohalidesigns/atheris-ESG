"""LLM access behind an adapter (CLAUDE.md rule 9; D12: Ollama serving IBM Granite). Tests use a fake client."""
from __future__ import annotations

import json
from typing import Protocol

import httpx


class LlmUnavailable(RuntimeError):
    pass


class LlmClient(Protocol):
    name: str

    def available(self) -> bool: ...

    def extract_json(self, system: str, user: str, schema: dict) -> dict: ...


class OllamaClient:
    def __init__(self, base_url: str, model: str, timeout: float = 180.0):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.name = f"{model} (Ollama)"
        self.timeout = timeout

    def available(self) -> bool:
        try:
            r = httpx.get(f"{self.base_url}/api/tags", timeout=3)
            return r.status_code == 200 and any(m.get("name") == self.model for m in r.json().get("models", []))
        except httpx.HTTPError:
            return False

    def extract_json(self, system: str, user: str, schema: dict) -> dict:
        body = {"model": self.model, "stream": False, "format": schema,
                "options": {"temperature": 0, "seed": 42, "num_ctx": 8192},
                "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}]}
        try:
            r = httpx.post(f"{self.base_url}/api/chat", json=body, timeout=self.timeout)
            r.raise_for_status()
        except httpx.HTTPError as e:
            raise LlmUnavailable(f"the language model could not be reached: {e}") from e
        try:
            return json.loads(r.json()["message"]["content"])
        except (KeyError, ValueError) as e:
            raise LlmUnavailable("the language model returned something that isn't JSON") from e
