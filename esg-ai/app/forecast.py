"""Emission forecasting (TRD §8.9 PredictiveEngine, D38 (2)).

ARIMA (statsmodels) when there is enough history, otherwise a linear trend; both with prediction intervals. A hold-out
backtest reports how well the same method would have predicted the most recent known periods. Deterministic: the same
series always gives the same forecast.
"""
from __future__ import annotations

import itertools
import warnings
from dataclasses import dataclass, field

import numpy as np
import statsmodels.api as sm
from statsmodels.tsa.arima.model import ARIMA

MIN_POINTS = {"month": 6, "year": 3}
ARIMA_FROM = {"month": 24, "year": 10}
MAX_HORIZON = {"month": 36, "year": 10}


class ForecastError(ValueError):
    """The request can't be forecast (too little data, bad periods)."""


@dataclass
class Fitted:
    method: str
    model: str
    mean: np.ndarray
    low: np.ndarray
    high: np.ndarray


@dataclass
class Forecast:
    method: str
    model: str
    n: int
    points: list[dict]
    backtest: dict | None
    warnings: list[str] = field(default_factory=list)


def _next_periods(last: str, frequency: str, horizon: int) -> list[str]:
    if frequency == "year":
        y = int(last)
        return [str(y + i) for i in range(1, horizon + 1)]
    y, m = (int(x) for x in last.split("-"))
    out = []
    for _ in range(horizon):
        m += 1
        if m > 12:
            y, m = y + 1, 1
        out.append(f"{y:04d}-{m:02d}")
    return out


def _check_consecutive(periods: list[str], frequency: str) -> None:
    for a, b in zip(periods, periods[1:]):
        if _next_periods(a, frequency, 1)[0] != b:
            raise ForecastError(f"periods must be consecutive: {a} is followed by {b}")


def _linear(y: np.ndarray, horizon: int, alpha: float) -> Fitted:
    t = np.arange(len(y), dtype=float)
    fit = sm.OLS(y, sm.add_constant(t)).fit()
    future = np.arange(len(y), len(y) + horizon, dtype=float)
    pred = fit.get_prediction(sm.add_constant(future, has_constant="add")).summary_frame(alpha=alpha)
    return Fitted("linear_trend", "OLS linear trend", pred["mean"].to_numpy(), pred["obs_ci_lower"].to_numpy(),
                  pred["obs_ci_upper"].to_numpy())


def _arima(y: np.ndarray, horizon: int, alpha: float) -> Fitted:
    best = None
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        for p, d, q in itertools.product((0, 1, 2), (0, 1), (0, 1)):
            try:
                res = ARIMA(y, order=(p, d, q), trend="t" if d == 1 else "c").fit()
            except Exception:  # noqa: BLE001 - an order that doesn't fit is skipped
                continue
            if np.isfinite(res.aic) and (best is None or res.aic < best[0]):
                best = (res.aic, (p, d, q), res)
    if best is None:
        return _linear(y, horizon, alpha)
    _, order, res = best
    fc = res.get_forecast(horizon)
    ci = fc.conf_int(alpha=alpha)
    return Fitted("arima", f"ARIMA{order}", np.asarray(fc.predicted_mean), np.asarray(ci[:, 0]), np.asarray(ci[:, 1]))


def _fit(y: np.ndarray, frequency: str, horizon: int, alpha: float) -> Fitted:
    return _arima(y, horizon, alpha) if len(y) >= ARIMA_FROM[frequency] else _linear(y, horizon, alpha)


def forecast(series: list[dict], frequency: str, horizon: int, level: float = 0.8) -> Forecast:
    if frequency not in MIN_POINTS:
        raise ForecastError("frequency must be month or year")
    if not 1 <= horizon <= MAX_HORIZON[frequency]:
        raise ForecastError(f"horizon must be 1 to {MAX_HORIZON[frequency]}")
    if not 0.5 <= level < 1:
        raise ForecastError("level must be at least 0.5 and below 1")
    periods = [str(p["period"]) for p in series]
    values = [float(p["value"]) for p in series]
    # Leading empty periods are before the organisation started recording.
    while values and values[0] == 0:
        periods, values = periods[1:], values[1:]
    if len(values) < MIN_POINTS[frequency]:
        raise ForecastError(f"at least {MIN_POINTS[frequency]} {frequency}s of data are needed; there are {len(values)}")
    _check_consecutive(periods, frequency)
    y = np.asarray(values, dtype=float)
    alpha = 1 - level
    fitted = _fit(y, frequency, horizon, alpha)
    notes = []
    if np.any(fitted.low < 0):
        notes.append("lower bounds below zero were set to zero")
    points = [{"period": p, "value": round(max(float(m), 0.0), 6), "low": round(max(float(lo), 0.0), 6), "high": round(max(float(hi), 0.0), 6)}
              for p, m, lo, hi in zip(_next_periods(periods[-1], frequency, horizon), fitted.mean, fitted.low, fitted.high)]
    return Forecast(fitted.method, fitted.model, len(y), points, backtest(y, frequency, alpha), notes)


def backtest(y: np.ndarray, frequency: str, alpha: float) -> dict | None:
    """Refit without the last periods and compare: mean absolute percentage error over the hold-out."""
    holdout = min(6 if frequency == "month" else 2, len(y) // 4)
    train = y[:-holdout] if holdout else y
    if holdout == 0 or len(train) < MIN_POINTS[frequency]:
        return None
    fitted = _fit(train, frequency, holdout, alpha)
    actual = y[-holdout:]
    mask = actual != 0
    if not mask.any():
        return None
    mape = float(np.mean(np.abs((actual[mask] - np.maximum(fitted.mean[mask], 0)) / actual[mask])) * 100)
    return {"holdout": int(holdout), "mape": round(mape, 1), "method": fitted.method}
