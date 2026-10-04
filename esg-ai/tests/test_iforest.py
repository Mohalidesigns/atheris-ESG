import pytest

from app.iforest import IsolationError, score


def series(group, values):
    return [{"id": f"{group}-{i}", "group": group, "seq": f"{i:03d}", "value": v} for i, v in enumerate(values)]


def test_a_spike_is_the_most_isolated_point_and_scores_are_repeatable():
    pts = series("site-a", [100, 102, 98, 101, 99, 103, 100, 97, 1000, 101]) + series("site-b", [50, 51, 49, 52, 50, 48, 51, 50, 49, 52])
    a = score(pts, 0.05)
    assert a == score(pts, 0.05)
    top = max(a, key=lambda s: s["score"])
    assert top["id"] == "site-a-8" and top["flagged"] and top["score"] == 1.0
    assert sum(s["flagged"] for s in a) <= 2


def test_refusals():
    with pytest.raises(IsolationError, match="at least 8"):
        score(series("a", [1, 2, 3]), 0.05)
    with pytest.raises(IsolationError, match="contamination"):
        score(series("a", list(range(10))), 0.5)
