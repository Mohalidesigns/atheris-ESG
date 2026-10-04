"""Isolation Forest outlier scoring (TRD §8.9 AnomalyDetector, D38 (3)).

Each point is described by where it sits in its own series (robust z-score against the series median) and how far it
moved from the previous period of that series; one forest is fitted over all series of a metric, so a point is unusual
relative to how every site behaves. Fixed random state: the same data always gives the same scores.
"""
from __future__ import annotations

from collections import defaultdict

import numpy as np
from sklearn.ensemble import IsolationForest

MIN_POINTS = 8


class IsolationError(ValueError):
    pass


def _features(points: list[dict]) -> np.ndarray:
    groups: dict[str, list[int]] = defaultdict(list)
    for i, p in enumerate(points):
        groups[str(p["group"])].append(i)
    feats = np.zeros((len(points), 2))
    for idx in groups.values():
        idx = sorted(idx, key=lambda i: str(points[i].get("seq", "")))
        v = np.array([float(points[i]["value"]) for i in idx])
        med = float(np.median(v))
        mad = float(np.median(np.abs(v - med))) * 1.4826
        scale = mad if mad > 0 else (float(np.std(v)) if np.std(v) > 0 else max(abs(med), 1.0))
        prev = None
        for i, val in zip(idx, v):
            feats[i, 0] = (val - med) / scale
            feats[i, 1] = 0.0 if prev is None else (val - prev) / max(abs(prev), scale, 1e-9)
            prev = val
    return feats


def score(points: list[dict], contamination: float) -> list[dict]:
    if len(points) < MIN_POINTS:
        raise IsolationError(f"at least {MIN_POINTS} values are needed; there are {len(points)}")
    if not 0.01 <= contamination <= 0.2:
        raise IsolationError("contamination must be between 0.01 and 0.2")
    x = _features(points)
    forest = IsolationForest(n_estimators=200, contamination=contamination, random_state=42).fit(x)
    raw = -forest.score_samples(x)  # higher = more isolated
    lo, hi = float(raw.min()), float(raw.max())
    norm = (raw - lo) / (hi - lo) if hi > lo else np.zeros_like(raw)
    flags = forest.predict(x) == -1
    return [{"id": p["id"], "score": round(float(s), 3), "flagged": bool(f), "zscore": round(float(z), 2), "change": round(float(c), 3)}
            for p, s, f, (z, c) in zip(points, norm, flags, x)]
