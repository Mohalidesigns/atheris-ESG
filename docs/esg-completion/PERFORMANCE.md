# Performance (WP-51, TRD §11.2.4)

Run: `perf/results/20261003-152821` (`./scripts/perf.sh`, k6 2.3). Machine: Apple silicon laptop, 10 cores, 16 GB;
one backend instance (JVM `-Xmx2g`, Hikari pool 10) and PostgreSQL 17 with default settings on the same machine.
Volume: 1,000,000 approved data points in the current year (200 sites × 20 metrics × 250 days), 10,000 Scope 1 records.

| TRD target | Result | Verdict |
|---|---|---|
| Users submitting data points, P95 < 500 ms | Open model (create + submit per submission): 100/s → P95 13 ms; 200/s → 11 ms; 300/s → 95 ms; 0 errors. Closed saturation, 200 users with no think time: 310 submissions/s (620 req/s), P95 626 ms | **Met** up to 300 submissions/s on one instance (≈18,000 people submitting once a minute). The no-think-time run shows the single-instance ceiling, not real users |
| Dashboard with 1M data points < 3 s | Executive dashboard + first data page, 10 concurrent viewers: P95 1.56 s (was 6.97 s before the fixes below) | **Met** |
| PDF report with 50,000 points < 60 s | ≈52,000 points: variables resolved and PDF rendered, max 0.77 s | **Met** |
| Emission batch of 10,000 records < 5 min | Recalculation of 10,000 records: 17.8 s | **Met** |
| Offline sync of 500 records < 30 s | Not measurable: the offline queue is WP-43, not built | **Open** |

## What the runs found and fixed

1. **Year filters scanned whole tables.** `extract(year from reporting_period_end) = ?` can't use an index under
   Row-Level Security (only leakproof comparisons on a column become index conditions), so each such query read every
   data point — ≈0.5 s at 1M, twice per dashboard. The large-table queries now compare the end date with plain bounds,
   backed by `(tenant_id, reporting_period_end)` indexes on data points and emission records (V036).
2. **"Which metrics have data" aggregated a year of points.** Compliance status (dashboard and maturity), maturity,
   scorecards and the SDG card now ask per catalogue metric with `exists (…)` on
   `(tenant_id, metric_key, validation_status, reporting_period_end)`: ≈540 ms → <1 ms.
3. **Recent activity walked a million audit rows** to find 25 status changes; status changes (updates) now have their
   own partial index on the audit log.
4. **The recalculation response listed every changed record** (≈0.5 MB for 10,000); it now lists the first 50 and
   counts all of them.

## Not measured

Multi-instance throughput, production database sizing, and browser rendering time (the dashboard figure is the API;
the page renders aggregated series). Offline sync waits for WP-43. Browser load time on 3G is WP-53.
