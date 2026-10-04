# Grounded AI (WP-50)

Analytics tabs **Forecasts (AI)** and **Report extraction (AI)**, and **ML scan (AI)** on the Anomalies tab. Design: D38
(runtime: D12). Migration V035 (`ai.runs`, `ai.extracted_values`; anomalies of kind `ml`). Service: `esg-ai/` (FastAPI).

Every AI output is a run: engine and model, parameters, what it was given, what it returned, who ran it and when. The
UI marks it *AI-generated* and names the engine; a person decides what to do with it. When the AI service isn't
configured or reachable, the API answers `ESG-503-AI-UNAVAILABLE` and the UI says so — nothing is estimated instead.

## Forecasts

`GET/POST /api/v1/analytics/predictions`, `GET /{id}`, `POST /{id}/dismiss` (`{note, version}`). Body:
`{target: emissions_total|scope_1|scope_2|scope_3|metric, metricKey, frequency: month|year, horizon, levelPct}`.
History: the last 60 complete months or 10 complete years of the caller's emissions (organisation scope), or a metric's
annual approved values. Model: ARIMA by AIC from 24 months / 10 years, OLS linear trend below; intervals; hold-out
back-test MAPE. The run keeps the history it used.

## Isolation Forest

`POST /analytics/ml-anomalies/scan` (`{year, contaminationPct 1–20}`), `GET /analytics/ml-anomalies/runs`. Scores five
years of submitted/approved values per metric across sites; values of the year it isolates become anomaly flags of
kind `ml` (method `iforest`, `mlScore`, `aiRunId`, `aiGenerated: true`), handled with the usual explain / accept /
dismiss actions on `/analytics/anomalies`.

## Report extraction

`POST /analytics/extractions` (`{fileId}` of an uploaded, clean PDF) starts a background run (202); `GET /extractions`,
`GET /extractions/{id}` → `{run, values}`. Each value: page, metric, number as written, unit, year, verbatim quote,
confidence. Kept only if the quote is on the page and contains the number (`run.output.dropped` lists the rest).
`POST /extractions/{id}/values/{valueId}/accept` (`{orgId, metricKey, value, periodStart, periodEnd, note, version}`)
creates a draft data point with the report as evidence; `.../reject` (`{note, version}`).

## Running it

`esg-ai/README.md`. Locally `scripts/dev.sh` starts it on :8090 when `esg-ai/.venv` exists; the backend's local
profile points there (`esg.ai.url`, `esg.ai.token`). Ollama serves the Granite model for extraction; forecasting and
Isolation Forest don't need it. Settings › Integrations shows the service's status.
