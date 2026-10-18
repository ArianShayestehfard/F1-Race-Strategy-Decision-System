from f1_strategy.ml import predict


def test_predict_contract(demo_db):
    res = predict("2024-8", "nor", "gradient_boosting")
    assert res["model"] == "gradient_boosting"
    assert res["mae_ms"] >= 0
    assert len(res["predictions"]) == 10
    assert all(p["predicted_lap_time_ms"] > 60_000 for p in res["predictions"])
    assert isinstance(res["tyre_degradation_ms_per_lap"], float)


def test_all_three_models_run(demo_db):
    for model in ("linear_regression", "random_forest", "gradient_boosting"):
        res = predict("2024-8", "nor", model)
        assert res["model"] == model
