import pytest

from app.forecast import ForecastError, forecast


def months(values, start=(2024, 1)):
    y, m = start
    out = []
    for v in values:
        out.append({"period": f"{y:04d}-{m:02d}", "value": v})
        m += 1
        if m > 12:
            y, m = y + 1, 1
    return out


def test_linear_trend_golden_values():
    # y = 10 + 2t exactly: the next three months are 22, 24, 26 and the interval collapses onto the line.
    f = forecast(months([10, 12, 14, 16, 18, 20]), "month", 3)
    assert f.method == "linear_trend"
    assert [p["period"] for p in f.points] == ["2024-07", "2024-08", "2024-09"]
    assert [p["value"] for p in f.points] == pytest.approx([22, 24, 26], abs=1e-6)
    assert all(p["low"] == pytest.approx(p["value"], abs=1e-4) and p["high"] == pytest.approx(p["value"], abs=1e-4) for p in f.points)


def test_yearly_series_and_backtest():
    f = forecast([{"period": str(2015 + i), "value": 100 + 5 * i} for i in range(8)], "year", 2)
    assert [p["period"] for p in f.points] == ["2023", "2024"]
    assert [p["value"] for p in f.points] == pytest.approx([140, 145], abs=1e-6)
    assert f.backtest == {"holdout": 2, "mape": 0.0, "method": "linear_trend"}


def test_arima_from_two_years_of_months_is_deterministic():
    values = [100 + (i % 12) * 3 + i * 0.5 for i in range(30)]
    a = forecast(months(values, (2022, 1)), "month", 6)
    b = forecast(months(values, (2022, 1)), "month", 6)
    assert a.method == "arima" and a.model.startswith("ARIMA(")
    assert a.points == b.points
    assert all(p["low"] <= p["value"] <= p["high"] for p in a.points)
    assert a.backtest["holdout"] == 6


def test_leading_empty_months_are_ignored_and_negatives_floored():
    f = forecast(months([0, 0, 50, 40, 30, 20, 10, 5]), "month", 3)
    assert f.n == 6
    assert all(p["value"] >= 0 and p["low"] >= 0 for p in f.points)
    assert "lower bounds below zero were set to zero" in f.warnings


@pytest.mark.parametrize("series,frequency,horizon,message", [
    (months([1, 2, 3]), "month", 3, "at least 6 months"),
    ([{"period": "2024-01", "value": 1}, {"period": "2024-03", "value": 2}] + months([3, 4, 5, 6], (2024, 4)), "month", 3, "consecutive"),
    (months([1, 2, 3, 4, 5, 6]), "month", 37, "horizon"),
    (months([1, 2, 3, 4, 5, 6]), "week", 3, "frequency"),
])
def test_refusals(series, frequency, horizon, message):
    with pytest.raises(ForecastError, match=message):
        forecast(series, frequency, horizon)
