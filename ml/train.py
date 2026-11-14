"""Treina o baseline ULB e avalia fraude em um conjunto temporal separado."""

import argparse
import csv
import hashlib
import json
import os
import platform
import sys
import tempfile
import warnings
from pathlib import Path

import joblib
import numpy as np
import scipy
import sklearn
from sklearn.exceptions import ConvergenceWarning
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    confusion_matrix,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from threadpoolctl import threadpool_limits

try:
    from .common import (
        DEFAULT_DATA,
        DEFAULT_MODEL,
        FEATURES,
        ML_DIR,
        MODEL_VERSION,
        SCHEMA_VERSION,
        TARGET,
    )
except ImportError:
    from common import (
        DEFAULT_DATA,
        DEFAULT_MODEL,
        FEATURES,
        ML_DIR,
        MODEL_VERSION,
        SCHEMA_VERSION,
        TARGET,
    )


def sha256_file(path: Path) -> str:
    """Calcula a identidade do arquivo sem carregá-lo inteiro na memória."""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def class_counts(labels: np.ndarray) -> dict[str, int]:
    """Conta separadamente transações legítimas e fraudulentas."""
    return {str(label): int(np.count_nonzero(labels == label)) for label in (0, 1)}


def load_dataset(path: Path) -> tuple[np.ndarray, np.ndarray, dict]:
    """Valida, remove duplicatas exatas e ordena o CSV pelo instante da transação.

    O segundo retorno conserva o índice original de cada registro, começando
    em zero e sem contar o cabeçalho, para identificar a origem dos exemplos.
    """
    with path.open("r", encoding="utf-8-sig", newline="") as source:
        header = next(csv.reader([source.readline()]), [])
        expected = [*FEATURES, TARGET]
        if header != expected:
            raise ValueError("Cabeçalho inválido. Esperado: " + ",".join(expected))
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            data = np.loadtxt(
                source,
                delimiter=",",
                quotechar='"',
                comments=None,
                dtype=np.float64,
                ndmin=2,
            )

    if data.shape[0] < 5 or data.shape[1] != len(expected):
        raise ValueError("O CSV precisa conter registros e todas as 31 colunas.")
    if not np.isfinite(data).all():
        raise ValueError("O CSV contém valores ausentes, NaN ou infinitos.")
    if np.any(data[:, 0] < 0) or np.any(data[:, -2] < 0):
        raise ValueError("Time e Amount devem ser maiores ou iguais a zero.")
    if not np.isin(data[:, -1], (0, 1)).all():
        raise ValueError("Class deve conter somente 0 (legítima) ou 1 (fraude).")

    original_rows = len(data)
    original_counts = class_counts(data[:, -1])
    _, source_indices = np.unique(data, axis=0, return_index=True)
    source_indices.sort()
    order = np.argsort(data[source_indices, 0], kind="stable")
    source_indices = source_indices[order]
    data = data[source_indices]
    return data, source_indices, {
        "original_rows": original_rows,
        "original_class_counts": original_counts,
        "rows_after_deduplication": len(data),
        "exact_duplicates_removed": original_rows - len(data),
        "class_counts_after_deduplication": class_counts(data[:, -1]),
    }


def chronological_split(data: np.ndarray) -> dict[str, slice]:
    """Separa aproximadamente 60/20/20, sem dividir um mesmo timestamp.

    Espera a matriz já ordenada por Time. Nenhuma observação de validação ou
    teste é usada para ajustar o scaler ou os coeficientes do modelo.
    """
    if len(data) < 5:
        raise ValueError("Dados insuficientes para treino, validação e teste.")
    times = data[:, 0]
    if np.any(times[1:] < times[:-1]):
        raise ValueError("Os dados devem estar ordenados por Time.")
    first_time = times[int(len(data) * 0.6)]
    second_time = times[int(len(data) * 0.8)]
    first = int(np.searchsorted(times, first_time, side="left"))
    second = int(np.searchsorted(times, second_time, side="left"))
    splits = {
        "train": slice(0, first),
        "validation": slice(first, second),
        "test": slice(second, len(data)),
    }
    for name, selected in splits.items():
        subset = data[selected]
        if not len(subset) or set(np.unique(subset[:, -1])) != {0, 1}:
            raise ValueError(
                f"O conjunto temporal '{name}' precisa conter as duas classes. "
                "Use o dataset completo; os limites temporais não são aleatorizados."
            )
    return splits


def choose_threshold(labels: np.ndarray, scores: np.ndarray) -> float:
    """Escolhe o maior F1 na validação; em empate, prefere o menor limiar."""
    precision, recall, thresholds = precision_recall_curve(labels, scores)
    denominator = precision[:-1] + recall[:-1]
    f1_values = np.divide(
        2 * precision[:-1] * recall[:-1],
        denominator,
        out=np.zeros_like(denominator),
        where=denominator != 0,
    )
    if not len(thresholds):
        raise ValueError("Não foi possível selecionar um limiar de decisão.")
    return float(thresholds[int(np.argmax(f1_values))])


def evaluate(labels: np.ndarray, scores: np.ndarray, threshold: float) -> dict:
    """Mede a classe fraude (1) e registra a matriz na ordem [legítima, fraude]."""
    predictions = (scores >= threshold).astype(np.int64)
    return {
        "precision": float(precision_score(labels, predictions, zero_division=0)),
        "recall": float(recall_score(labels, predictions, zero_division=0)),
        "f1": float(f1_score(labels, predictions, zero_division=0)),
        "average_precision": float(average_precision_score(labels, scores)),
        "roc_auc": float(roc_auc_score(labels, scores)),
        "accuracy": float(accuracy_score(labels, predictions)),
        "confusion_matrix": confusion_matrix(labels, predictions, labels=[0, 1]).tolist(),
        "confusion_matrix_labels": [0, 1],
    }


def write_json(path: Path, value: dict) -> None:
    """Publica JSON somente após terminar a gravação no mesmo diretório."""
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", dir=path.parent, suffix=".tmp", delete=False
        ) as destination:
            temporary = Path(destination.name)
            json.dump(value, destination, indent=2, ensure_ascii=False, allow_nan=False)
            destination.write("\n")
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def validate_output_paths(
    data_path: Path, model_path: Path, report_path: Path, examples_dir: Path
) -> None:
    """Impede que os caminhos de saída substituam o CSV ou uns aos outros."""
    paths = [
        data_path.resolve(),
        model_path.resolve(),
        report_path.resolve(),
        (examples_dir / "legitima.json").resolve(),
        (examples_dir / "fraude.json").resolve(),
    ]
    if len(set(paths)) != len(paths):
        raise ValueError("Dataset, modelo, relatório e exemplos devem ter caminhos distintos.")
    for path in paths[1:]:
        if path.is_dir():
            raise ValueError(f"O arquivo de saída aponta para um diretório: {path}")
        if any(other in path.parents for other in paths):
            raise ValueError("Um arquivo de entrada/saída não pode ser diretório de outro.")
    if examples_dir.exists() and not examples_dir.is_dir():
        raise ValueError("O caminho dos exemplos precisa ser um diretório.")


def train(data_path: Path, model_path: Path, report_path: Path, examples_dir: Path) -> dict:
    """Treina uma vez, escolhe o limiar na validação e publica artefatos locais."""
    validate_output_paths(data_path, model_path, report_path, examples_dir)
    print("Carregando e validando o dataset...")
    data, source_indices, dataset_info = load_dataset(data_path)
    splits = chronological_split(data)
    dataset_info.update({"filename": data_path.name, "sha256": sha256_file(data_path)})

    training = data[splits["train"]]
    validation = data[splits["validation"]]
    testing = data[splits["test"]]
    pipeline = Pipeline(
        [
            ("scaler", StandardScaler()),
            (
                "classifier",
                LogisticRegression(
                    class_weight="balanced", solver="lbfgs", max_iter=2000, random_state=42
                ),
            ),
        ]
    )
    print(
        f"Treino: {len(training)} | validação: {len(validation)} | teste: {len(testing)}."
    )
    with threadpool_limits(limits=2), warnings.catch_warnings():
        warnings.simplefilter("error", ConvergenceWarning)
        try:
            pipeline.fit(training[:, :-1], training[:, -1].astype(np.int64))
        except ConvergenceWarning as error:
            raise ValueError("A regressão logística não convergiu; o modelo não foi salvo.") from error
        validation_scores = pipeline.predict_proba(validation[:, :-1])[:, 1]
        threshold = choose_threshold(validation[:, -1], validation_scores)
        test_scores = pipeline.predict_proba(testing[:, :-1])[:, 1]

    artifact = {
        "schema_version": SCHEMA_VERSION,
        "model_version": MODEL_VERSION,
        "feature_names": list(FEATURES),
        "threshold": threshold,
        "sklearn_version": sklearn.__version__,
        "pipeline": pipeline,
    }
    report = {
        "dataset": dataset_info,
        "split_strategy": "chronological_60_20_20_no_shared_timestamps_after_exact_deduplication",
        "splits": {
            name: {
                "rows": len(data[selected]),
                "class_counts": class_counts(data[selected, -1]),
                "time_min": float(data[selected, 0].min()),
                "time_max": float(data[selected, 0].max()),
            }
            for name, selected in splits.items()
        },
        "model": {
            "version": MODEL_VERSION,
            "schema_version": SCHEMA_VERSION,
            "algorithm": "StandardScaler + LogisticRegression",
            "features": list(FEATURES),
            "class_weight": "balanced",
            "solver": "lbfgs",
            "max_iter": 2000,
            "random_state": 42,
            "threshold": threshold,
            "threshold_selection": "maximum_validation_f1_ties_choose_lowest_threshold",
            "refit_after_validation": False,
            "score_is_calibrated_probability": False,
        },
        "environment": {
            "python": platform.python_version(),
            "numpy": np.__version__,
            "scipy": scipy.__version__,
            "scikit_learn": sklearn.__version__,
            "joblib": joblib.__version__,
        },
        "validation": evaluate(validation[:, -1], validation_scores, threshold),
        "test": evaluate(testing[:, -1], test_scores, threshold),
        "baseline_always_legitimate": evaluate(
            testing[:, -1], np.zeros(len(testing)), threshold=0.5
        ),
        "examples": {},
    }

    # Os exemplos são escolhidos pelo rótulo verdadeiro, nunca pelo acerto do modelo.
    example_payloads = {}
    test_source_indices = source_indices[splits["test"]]
    for label, filename in ((0, "legitima.json"), (1, "fraude.json")):
        index = int(np.flatnonzero(testing[:, -1] == label)[0])
        row = testing[index]
        example_payloads[filename] = dict(zip(FEATURES, map(float, row[:-1])))
        report["examples"][filename] = {
            "selection": "first_test_row_with_this_true_class",
            "dataset_record_index_zero_based": int(test_source_indices[index]),
            "csv_line_number_including_header": int(test_source_indices[index]) + 2,
            "true_class": label,
            "predicted_class": int(test_scores[index] >= threshold),
            "score": float(test_scores[index]),
        }

    model_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=model_path.parent, suffix=".tmp", delete=False) as file:
            temporary = Path(file.name)
        joblib.dump(artifact, temporary, compress=3)
        report["model"]["sha256"] = sha256_file(temporary)
        os.replace(temporary, model_path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    for filename, payload in example_payloads.items():
        write_json(examples_dir / filename, payload)
    write_json(report_path, report)
    print(f"Modelo salvo em: {model_path}")
    print(f"Relatório salvo em: {report_path}")
    print(f"Limiar escolhido somente na validação: {threshold:.8f}")
    print(
        "Teste temporal — "
        f"precisão: {report['test']['precision']:.2%}; "
        f"recall: {report['test']['recall']:.2%}; "
        f"F1: {report['test']['f1']:.4f}; "
        f"AP: {report['test']['average_precision']:.4f}."
    )
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=DEFAULT_DATA, help="CSV ULB completo.")
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL, help="Artefato .pkl de saída.")
    parser.add_argument(
        "--report", type=Path, default=ML_DIR / "reports" / "metrics.json", help="Métricas JSON."
    )
    parser.add_argument(
        "--examples-dir", type=Path, default=ML_DIR / "examples", help="Pasta dos exemplos JSON."
    )
    args = parser.parse_args()
    try:
        train(args.data, args.model, args.report, args.examples_dir)
    except (OSError, ValueError, csv.Error) as error:
        print(f"Erro no treinamento: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
