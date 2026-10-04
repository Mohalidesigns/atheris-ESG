# Performance runs (WP-51, TRD §11.2.4)

`./scripts/perf.sh` builds the backend, creates a **throwaway** `esg_perf` database (local demo tenant), seeds volume
(`seed-volume.sql`: 1,000,000 approved data points in the current year across 200 sites × 20 metrics, 10,000 Scope 1
records in 2024), settles it (VACUUM ANALYZE, CHECKPOINT) and runs k6. Results land in `perf/results/<run>/`
(`*.txt` k6 output, `*.json` summaries, `MACHINE.txt`). Needs k6 (`brew install k6`).

| Script | TRD target | What it does |
|---|---|---|
| `k6/submit.js` | 10,000 concurrent users submitting, P95 < 500 ms | Create + submit a data point; open model at `RATES` submissions/s (default 100, 200, 300), then a closed saturation run (`VUS`, no think time). Every (site, metric, day) is unique, so any refusal is a real error |
| `k6/dashboard.js` | dashboard with 1M points < 3 s | Executive dashboard (all cards) + the first page of data points, 10 concurrent viewers |
| `k6/report.js` | report with 50,000 points, PDF < 60 s | New report over 13 days (~52,000 approved points): save sections (resolves every variable) + render the PDF |
| `k6/calculate.js` | 10,000-record emission batch < 5 min | `POST /carbon/calculate` over 2024: factor selection and arithmetic for 10,000 records |

Examples: `RATES="50 400" HOLD=120s ./scripts/perf.sh submit`, `POINTS=200000 ./scripts/perf.sh dashboard`.

Not covered: offline sync of 500 queued records (TRD) — the offline queue is WP-43, not built yet.

**Reading the results.** One backend instance and PostgreSQL share one laptop, with PostgreSQL's default settings
(128 MB shared buffers). "10,000 concurrent users" is a population, not 10,000 requests in flight: at one submission
per user per minute it is ~167 submissions/s, which the open-model runs cover; the saturation run shows where a single
instance on this machine tops out. Production sizing (instances, database) is a deployment decision, not measured here.
