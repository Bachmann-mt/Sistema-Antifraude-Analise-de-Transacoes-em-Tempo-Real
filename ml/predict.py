"""Infere uma transação e escreve somente o resultado JSON em stdout."""

import argparse
from contextlib import redirect_stdout
import json
import math
from pathlib import Path
import sys
import warnings

from common import DEFAULT_MODEL, FEATURES, SCHEMA_VERSION, parse_transaction


def predict(values, model_path):
    """Carrega um artefato local confiável e aplica o mesmo pipeline do treino."""
    # Bibliotecas e eventuais avisos nunca devem contaminar o stdout do Java.
    with redirect_stdout(sys.stderr):
        import joblib
        import numpy as np
        import sklearn
        from sklearn.exceptions import InconsistentVersionWarning

        with warnings.catch_warnings():
            warnings.simplefilter("error", InconsistentVersionWarning)
            artifact = joblib.load(model_path)
        if not isinstance(artifact, dict):
            raise ValueError("Formato de modelo inválido; execute train.py novamente.")
        if artifact.get("schema_version") != SCHEMA_VERSION:
            raise ValueError("Versão do contrato do modelo incompatível.")
        if artifact.get("feature_names") != list(FEATURES):
            raise ValueError("As features do modelo não correspondem ao contrato de entrada.")
        if artifact.get("sklearn_version") != sklearn.__version__:
            raise ValueError("Versão do scikit-learn incompatível; instale requirements.txt e retreine.")
        threshold = artifact.get("threshold")
        if (isinstance(threshold, bool) or not isinstance(threshold, (int, float))
                or not math.isfinite(threshold) or not 0 <= threshold <= 1):
            raise ValueError("Limiar do modelo inválido.")
        version = artifact.get("model_version")
        if not isinstance(version, str) or not version:
            raise ValueError("Identificador do modelo ausente.")
        pipeline = artifact.get("pipeline")
        if (getattr(pipeline, "n_features_in_", None) != len(FEATURES)
                or list(getattr(pipeline, "classes_", [])) != [0, 1]):
            raise ValueError("Pipeline incompatível com o contrato de fraude.")
        probabilities = pipeline.predict_proba(np.array([values], dtype=np.float64))
        if probabilities.shape != (1, 2):
            raise ValueError("Formato inesperado de saída do modelo.")
        score = float(probabilities[0, 1])
        if not math.isfinite(score) or not 0 <= score <= 1:
            raise ValueError("O modelo produziu um score inválido.")

    # Decide com a precisão original, sem arredondar antes de comparar.
    return {
        "status": "FRAUDE" if score >= threshold else "APROVADO",
        "score_fraude": score,
        "limiar": threshold,
        "modelo": version,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("transacao", nargs="?", help="Objeto JSON como um único argumento.")
    parser.add_argument("--input", type=Path, help="Arquivo JSON UTF-8 (alternativa ao argumento).")
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL, help="Modelo .pkl local confiável.")
    args = parser.parse_args(argv)
    if (args.transacao is None) == (args.input is None):
        parser.error("informe exatamente uma entrada: transacao ou --input")

    try:
        text = args.input.read_text(encoding="utf-8-sig") if args.input else args.transacao
        values = parse_transaction(text)
    except (ValueError, OSError, UnicodeError) as exc:
        print(json.dumps({"erro": str(exc)}, ensure_ascii=True), file=sys.stderr)
        return 2

    if not args.model.is_file():
        print(json.dumps({"erro": "Modelo não encontrado. Execute python ml/train.py primeiro."},
                         ensure_ascii=True), file=sys.stderr)
        return 1
    try:
        result = predict(values, args.model)
    except Exception as exc:
        print(json.dumps({"erro": f"Falha ao carregar/aplicar o modelo: {exc}"},
                         ensure_ascii=True), file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=True, allow_nan=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
