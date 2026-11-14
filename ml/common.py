"""Contrato compartilhado pelo treinamento e pela inferência."""

import json
import math
from pathlib import Path

ML_DIR = Path(__file__).resolve().parent
DEFAULT_DATA = ML_DIR / "data" / "creditcard.csv"
DEFAULT_MODEL = ML_DIR / "models" / "fraud_model.pkl"
FEATURES = ("Time", *(f"V{i}" for i in range(1, 29)), "Amount")
TARGET = "Class"
SCHEMA_VERSION = 1
MODEL_VERSION = "ulb-logreg-v1"
DATASET_URL = "https://storage.googleapis.com/download.tensorflow.org/data/creditcard.csv"


def reject_duplicate_keys(pairs):
    """Evita aceitar silenciosamente dois valores para o mesmo campo JSON."""
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Campo repetido no JSON: {key}.")
        result[key] = value
    return result


def parse_transaction(text):
    """Converte um objeto JSON válido na ordem exata das features de treino."""
    try:
        payload = json.loads(text, object_pairs_hook=reject_duplicate_keys)
    except json.JSONDecodeError as exc:
        raise ValueError("Entrada deve ser um objeto JSON válido.") from exc
    if not isinstance(payload, dict):
        raise ValueError("Entrada deve ser um objeto JSON com os campos da transação.")

    missing = sorted(set(FEATURES) - payload.keys())
    extra = sorted(payload.keys() - set(FEATURES))
    if missing or extra:
        details = []
        if missing:
            details.append("campos ausentes: " + ", ".join(missing))
        if extra:
            details.append("campos desconhecidos: " + ", ".join(extra))
        raise ValueError("Contrato inválido; " + "; ".join(details) + ".")

    values = []
    for name in FEATURES:
        value = payload[name]
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValueError(f"{name} deve ser um número JSON, sem aspas.")
        try:
            numeric = float(value)
        except OverflowError as exc:
            raise ValueError(f"{name} está fora do intervalo numérico suportado.") from exc
        if not math.isfinite(numeric):
            raise ValueError(f"{name} deve ser finito (sem NaN ou infinito).")
        if name in ("Time", "Amount") and numeric < 0:
            raise ValueError(f"{name} deve ser maior ou igual a zero.")
        values.append(numeric)
    return values
