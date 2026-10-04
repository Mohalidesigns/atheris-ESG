"""Grounded extraction from sustainability reports (TRD §8.9 NLPExtractor, D38 (4)).

The language model proposes values for catalogue metrics, each with a verbatim quote. A proposal is kept only if the
quote really is on the cited page and the number really is in the quote; everything else is dropped and counted. The
result is a proposal for a person to confirm — nothing here writes data.
"""
from __future__ import annotations

import io
import re
import unicodedata
from dataclasses import dataclass, field

from pypdf import PdfReader
from pypdf.errors import PdfReadError

from .llm import LlmClient

MAX_PAGES = 200
MAX_PAGES_READ = 30
MAX_CHARS = 6000
NUMBER = re.compile(r"(?<![\w.])[-−]?\d{1,3}(?:[,  ]\d{3})+(?:\.\d+)?|(?<![\w.])[-−]?\d+(?:\.\d+)?")

SYSTEM = (
    "You extract ESG figures from one page of a company report. Use only what is written on the page. "
    "For each figure that matches one of the listed metrics, return the metric key, the number exactly as written, "
    "the unit as written, the reporting year if stated, and a quote copied word for word from the page that contains "
    "the number. If nothing matches, return an empty list. Never estimate, convert or calculate."
)

SCHEMA = {
    "type": "object",
    "properties": {
        "values": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "metric_key": {"type": "string"},
                    "value": {"type": "string"},
                    "unit": {"type": "string"},
                    "year": {"type": ["integer", "null"]},
                    "quote": {"type": "string"},
                },
                "required": ["metric_key", "value", "unit", "quote"],
            },
        }
    },
    "required": ["values"],
}


class ExtractionError(ValueError):
    pass


@dataclass
class Extraction:
    pages_total: int
    pages_read: list[int]
    values: list[dict] = field(default_factory=list)
    dropped: list[dict] = field(default_factory=list)


def normalise(text: str) -> str:
    text = unicodedata.normalize("NFKC", text).replace("−", "-").replace("’", "'").replace("“", '"').replace("”", '"')
    return re.sub(r"\s+", " ", text).strip().lower()


def to_number(raw: str) -> float | None:
    s = str(raw).strip().replace("−", "-").replace(" ", "").replace(" ", "").replace(",", "")
    try:
        return float(s)
    except ValueError:
        return None


def numbers_in(text: str) -> list[float]:
    out = []
    for m in NUMBER.findall(text):
        n = to_number(m)
        if n is not None:
            out.append(n)
    return out


def pages(pdf: bytes) -> list[str]:
    try:
        reader = PdfReader(io.BytesIO(pdf))
        if reader.is_encrypted:
            raise ExtractionError("the PDF is password-protected")
        if len(reader.pages) > MAX_PAGES:
            raise ExtractionError(f"the PDF has more than {MAX_PAGES} pages")
        return [p.extract_text() or "" for p in reader.pages]
    except PdfReadError as e:
        raise ExtractionError("the file isn't a readable PDF") from e


def _keywords(metrics: list[dict]) -> list[str]:
    words = set()
    for m in metrics:
        for w in re.split(r"[\W_]+", f"{m['key']} {m.get('name', '')}".lower()):
            if len(w) >= 5:
                words.add(w)
    return sorted(words)


def candidate_pages(texts: list[str], metrics: list[dict]) -> list[int]:
    """Pages (1-based) with numbers and at least one metric word, most relevant first, capped."""
    words = _keywords(metrics)
    scored = []
    for i, t in enumerate(texts, start=1):
        low = t.lower()
        hits = sum(low.count(w) for w in words)
        if hits and numbers_in(t):
            scored.append((-hits, i))
    return sorted(i for _, i in sorted(scored)[:MAX_PAGES_READ])


def verify(proposal: dict, page_text: str, metric_keys: set[str]) -> str | None:
    """None when the proposal is grounded; otherwise why it was dropped."""
    if proposal.get("metric_key") not in metric_keys:
        return "not a catalogue metric"
    value = to_number(proposal.get("value", ""))
    if value is None:
        return "the value isn't a number"
    quote = normalise(proposal.get("quote", ""))
    if len(quote) < 8:
        return "no quote"
    if quote not in normalise(page_text):
        return "the quote isn't on the page"
    if not any(abs(n - value) <= 1e-9 * max(1.0, abs(value)) for n in numbers_in(quote)):
        return "the number isn't in the quote"
    return None


def extract(pdf: bytes, metrics: list[dict], llm: LlmClient) -> Extraction:
    if not metrics:
        raise ExtractionError("no metrics to look for")
    texts = pages(pdf)
    if not any(t.strip() for t in texts):
        raise ExtractionError("the PDF has no text layer (a scanned document); text recognition isn't supported")
    keys = {m["key"] for m in metrics}
    catalogue = "\n".join(f"- {m['key']}: {m.get('name', '')} ({m.get('unit', '')})" for m in metrics)
    read = candidate_pages(texts, metrics)
    result = Extraction(pages_total=len(texts), pages_read=read)
    seen = set()
    for page in read:
        text = texts[page - 1][:MAX_CHARS]
        answer = llm.extract_json(SYSTEM, f"Metrics:\n{catalogue}\n\nPage {page}:\n{text}", SCHEMA)
        for p in answer.get("values", []) if isinstance(answer, dict) else []:
            if not isinstance(p, dict):
                continue
            reason = verify(p, texts[page - 1], keys)
            item = {"page": page, "metric_key": p.get("metric_key"), "value": to_number(p.get("value", "")), "raw_value": p.get("value"),
                    "unit": (p.get("unit") or "").strip()[:50], "year": p.get("year") if isinstance(p.get("year"), int) else None,
                    "quote": re.sub(r"\s+", " ", str(p.get("quote", ""))).strip()[:500]}
            if reason:
                result.dropped.append({**item, "reason": reason})
                continue
            key = (item["metric_key"], item["value"], item["year"], page)
            if key in seen:
                continue
            seen.add(key)
            unit_seen = bool(item["unit"]) and normalise(item["unit"]) in normalise(item["quote"])
            result.values.append({**item, "confidence": "high" if unit_seen else "medium"})
    return result
