import io

import pytest
from fastapi.testclient import TestClient
from reportlab.pdfgen import canvas

from app.extract import ExtractionError, extract, normalise, numbers_in, verify
from app.main import create_app

METRICS = [{"key": "scope1_emissions", "name": "Scope 1 emissions", "unit": "tCO2e"},
           {"key": "water_withdrawal", "name": "Water withdrawal", "unit": "m3"}]


def pdf(pages):
    buf = io.BytesIO()
    c = canvas.Canvas(buf)
    for lines in pages:
        y = 800
        for line in lines:
            c.drawString(50, y, line)
            y -= 20
        c.showPage()
    c.save()
    return buf.getvalue()


REPORT = pdf([["Chairman's statement", "We remain committed to our people."],
              ["Environmental performance", "Total Scope 1 emissions were 12,450 tCO2e in 2024.", "Water withdrawal fell to 3,200 m3."]])


class FakeLlm:
    name = "fake"

    def __init__(self, answer):
        self.answer = answer
        self.pages = []

    def available(self):
        return True

    def extract_json(self, system, user, schema):
        self.pages.append(user.split("Page ")[1].split(":")[0])
        return self.answer


def test_only_grounded_values_are_kept():
    llm = FakeLlm({"values": [
        {"metric_key": "scope1_emissions", "value": "12,450", "unit": "tCO2e", "year": 2024, "quote": "Total Scope 1 emissions were 12,450 tCO2e in 2024."},
        {"metric_key": "scope1_emissions", "value": "15000", "unit": "tCO2e", "quote": "Total Scope 1 emissions were 15,000 tCO2e"},
        {"metric_key": "water_withdrawal", "value": "3,500", "unit": "m3", "quote": "Water withdrawal fell to 3,200 m3."},
        {"metric_key": "made_up_metric", "value": "7", "unit": "", "quote": "Water withdrawal fell to 3,200 m3."},
    ]})
    x = extract(REPORT, METRICS, llm)
    assert x.pages_total == 2 and x.pages_read == [2] and llm.pages == ["2"]
    assert len(x.values) == 1
    v = x.values[0]
    assert (v["metric_key"], v["value"], v["unit"], v["year"], v["page"], v["confidence"]) == ("scope1_emissions", 12450.0, "tCO2e", 2024, 2, "high")
    assert sorted(d["reason"] for d in x.dropped) == ["not a catalogue metric", "the number isn't in the quote", "the quote isn't on the page"]


def test_helpers():
    assert numbers_in("12,450 tCO2e and 3.5% of 1 200") == [12450.0, 3.5, 1200.0]
    assert normalise("Scope  1\n emissions – “total”") == 'scope 1 emissions – "total"'
    assert verify({"metric_key": "x", "value": "1", "quote": "short"}, "short", {"x"}) == "no quote"


def test_unreadable_documents_are_refused():
    with pytest.raises(ExtractionError, match="readable PDF"):
        extract(b"not a pdf", METRICS, FakeLlm({"values": []}))
    with pytest.raises(ExtractionError, match="no text layer"):
        extract(pdf([[""]]), METRICS, FakeLlm({"values": []}))


def test_api_requires_the_service_token_and_answers_problem_json():
    client = TestClient(create_app(llm=FakeLlm({"values": []}), token="t0ken"))
    assert client.post("/v1/forecast", json={"series": [], "frequency": "month", "horizon": 1}).status_code == 401
    r = client.post("/v1/forecast", headers={"X-AI-Token": "t0ken"}, json={"series": [], "frequency": "month", "horizon": 1})
    assert r.status_code == 422 and r.json()["errorCode"] == "ESG-422-AI-INPUT"
    r = client.post("/v1/extract", headers={"X-AI-Token": "t0ken"}, files={"file": ("r.pdf", REPORT, "application/pdf")},
                    data={"metrics": '[{"key": "scope1_emissions", "name": "Scope 1 emissions", "unit": "tCO2e"}]'})
    assert r.status_code == 200 and r.json()["pagesRead"] == [2] and r.json()["values"] == []
    assert client.get("/v1/health").json()["llm"] == "fake"
