"""Testes locais do contrato CLI e do isolamento entre treino/validação/teste.

As fixtures sintéticas verificam o software, não a qualidade em fraude real.
Nenhum teste acessa a rede ou depende do dataset/modelo baixados pelo usuário.
"""

import csv
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import joblib
import numpy as np
import sklearn
from sklearn.dummy import DummyClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

ML_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ML_DIR))

import common
import download_data
import train


def transaction(**changes):
    payload = {name: 0.0 for name in common.FEATURES}
    payload.update(Time=10.0, Amount=25.0)
    payload.update(changes)
    return payload


def synthetic_rows(count=200):
    """Alterna classes ao longo do tempo para permitir três períodos distintos."""
    rng = np.random.default_rng(42)
    features = rng.normal(size=(count, len(common.FEATURES)))
    labels = np.arange(count) % 2
    features[:, 0] = np.arange(count)
    features[:, 1] = labels * 4 - 2 + rng.normal(0, 0.1, count)
    features[:, -1] = 10 + np.arange(count) * 0.5
    return np.column_stack((features, labels))


def write_dataset(path, rows):
    with path.open("w", encoding="utf-8", newline="") as destination:
        # O CSV original também usa aspas em campos numéricos, incluindo Class.
        writer = csv.writer(destination, quoting=csv.QUOTE_ALL)
        writer.writerow([*common.FEATURES, common.TARGET])
        writer.writerows(rows)


class PredictionContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workspace = tempfile.TemporaryDirectory(prefix="fraud-cli-tests-")
        cls.addClassCleanup(cls.workspace.cleanup)
        cls.directory = Path(cls.workspace.name)
        rows = synthetic_rows(40)
        cls.pipeline = Pipeline([
            ("scaler", StandardScaler()),
            ("classifier", LogisticRegression(random_state=42)),
        ]).fit(rows[:, :-1], rows[:, -1])
        cls.artifact = {
            "schema_version": common.SCHEMA_VERSION,
            "model_version": "fixture-only",
            "feature_names": list(common.FEATURES),
            "threshold": 0.5,
            "sklearn_version": sklearn.__version__,
            "pipeline": cls.pipeline,
        }
        cls.model = cls.directory / "fixture.pkl"
        joblib.dump(cls.artifact, cls.model)

    def invoke(self, *arguments, model=None):
        # Usar cwd externo detecta caminhos indevidamente relativos ao terminal.
        return subprocess.run(
            [sys.executable, str(ML_DIR / "predict.py"), *map(str, arguments),
             "--model", str(model if model is not None else self.model)],
            cwd=self.directory, text=True, encoding="utf-8", errors="replace",
            capture_output=True, timeout=30, check=False,
        )

    def assert_success(self, result):
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, "")
        self.assertEqual(len(result.stdout.splitlines()), 1)
        payload = json.loads(result.stdout)
        self.assertEqual(set(payload), {"status", "score_fraude", "limiar", "modelo"})
        self.assertIn(payload["status"], ("APROVADO", "FRAUDE"))
        self.assertGreaterEqual(payload["score_fraude"], 0)
        self.assertLessEqual(payload["score_fraude"], 1)
        self.assertEqual(payload["status"],
                         "FRAUDE" if payload["score_fraude"] >= payload["limiar"] else "APROVADO")
        return payload

    def assert_failure(self, result, code=None):
        self.assertNotEqual(result.returncode, 0)
        if code is not None:
            self.assertEqual(result.returncode, code, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertTrue(result.stderr.strip())
        self.assertNotIn("Traceback", result.stderr)

    def test_positional_json_emits_only_one_result(self):
        for value, expected in ((-4.0, "APROVADO"), (4.0, "FRAUDE")):
            with self.subTest(value=value):
                payload = self.assert_success(self.invoke(json.dumps(transaction(V1=value))))
                self.assertEqual(payload["status"], expected)
                self.assertEqual(payload["modelo"], "fixture-only")

    def test_json_file_with_bom_works_from_another_directory(self):
        source = self.directory / "transacao com espacos.json"
        source.write_text(json.dumps(transaction(V1=-4.0)), encoding="utf-8-sig")
        file_result = self.assert_success(self.invoke("--input", source))
        argument_result = self.assert_success(self.invoke(json.dumps(transaction(V1=-4.0))))
        self.assertEqual(file_result, argument_result)

    def test_invalid_inputs_never_pollute_stdout(self):
        missing = transaction()
        del missing["V28"]
        invalid = {
            "malformed": "{",
            "array": "[]",
            "null": "null",
            "missing": json.dumps(missing),
            "extra": json.dumps(transaction(Class=1)),
            "bool": json.dumps(transaction(V1=True)),
            "string_number": json.dumps(transaction(V1="2.0")),
            "nan": json.dumps(transaction(V1=float("nan"))),
            "infinity": json.dumps(transaction(V1=float("inf"))),
            "negative_time": json.dumps(transaction(Time=-1)),
            "negative_amount": json.dumps(transaction(Amount=-1)),
            "overflow": json.dumps(transaction(V1=10**400)),
            "duplicate": json.dumps(transaction())[:-1] + ', "V1": 5}',
        }
        for name, payload in invalid.items():
            with self.subTest(name=name):
                result = self.invoke(payload)
                self.assert_failure(result, 2)
                self.assertIn("erro", json.loads(result.stderr))

    def test_exactly_one_input_is_required(self):
        source = self.directory / "valid.json"
        source.write_text(json.dumps(transaction()), encoding="utf-8")
        for arguments in ((), (json.dumps(transaction()), "--input", source)):
            with self.subTest(arguments=arguments):
                self.assert_failure(self.invoke(*arguments), 2)

    def test_missing_or_unreadable_input_file_fails_cleanly(self):
        invalid_utf8 = self.directory / "invalid-utf8.json"
        invalid_utf8.write_bytes(b"\xff\xfe\x00")
        for source in (self.directory / "absent.json", invalid_utf8):
            with self.subTest(source=source):
                self.assert_failure(self.invoke("--input", source), 2)

    def test_missing_and_corrupt_models_fail_cleanly(self):
        corrupt = self.directory / "corrupt.pkl"
        corrupt.write_bytes(b"This is not a model")
        for model in (self.directory / "absent.pkl", corrupt):
            with self.subTest(model=model):
                result = self.invoke(json.dumps(transaction()), model=model)
                self.assert_failure(result, 1)
                self.assertIn("erro", json.loads(result.stderr))

    def test_incompatible_model_metadata_is_rejected(self):
        variants = {
            "schema": {"schema_version": -1},
            "features": {"feature_names": list(reversed(common.FEATURES))},
            "library": {"sklearn_version": "0.0.0"},
            "threshold": {"threshold": float("nan")},
            "pipeline": {"pipeline": None},
        }
        for name, changes in variants.items():
            with self.subTest(name=name):
                path = self.directory / f"incompatible-{name}.pkl"
                joblib.dump({**self.artifact, **changes}, path)
                self.assert_failure(self.invoke(json.dumps(transaction()), model=path), 1)

    def test_threshold_equality_and_unrounded_scores(self):
        features = np.zeros((4, len(common.FEATURES)))
        constant = Pipeline([
            ("scaler", StandardScaler()),
            ("classifier", DummyClassifier(strategy="prior")),
        ]).fit(features, [0, 0, 1, 1])
        for threshold, status in ((0.5, "FRAUDE"), (0.50000001, "APROVADO")):
            with self.subTest(threshold=threshold):
                model = self.directory / "boundary.pkl"
                joblib.dump({**self.artifact, "pipeline": constant, "threshold": threshold}, model)
                result = self.assert_success(self.invoke(json.dumps(transaction()), model=model))
                self.assertEqual(result["score_fraude"], 0.5)
                self.assertEqual(result["status"], status)


class TrainingIsolationTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="fraud-training-tests-")
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.dataset = self.directory / "synthetic.csv"

    def test_load_removes_duplicates_before_splitting_and_preserves_origin(self):
        rows = synthetic_rows()
        raw = np.vstack((rows[::-1], rows[[10, 119, 160, 199]]))
        write_dataset(self.dataset, raw)
        data, indices, information = train.load_dataset(self.dataset)
        self.assertEqual(information["original_rows"], 204)
        self.assertEqual(information["exact_duplicates_removed"], 4)
        self.assertEqual(information["rows_after_deduplication"], 200)
        np.testing.assert_allclose(data, raw[indices])
        self.assertTrue(np.all(data[1:, 0] >= data[:-1, 0]))
        self.assertEqual(len(np.unique(data, axis=0)), len(data))
        splits = train.chronological_split(data)
        self.assertEqual([len(data[s]) for s in splits.values()], [120, 40, 40])

    def test_temporal_partitions_do_not_share_timestamps_or_rows(self):
        data = synthetic_rows()
        data[:, 0] = np.arange(len(data)) // 7
        splits = train.chronological_split(data)
        train_rows, validation_rows, test_rows = (data[s] for s in splits.values())
        self.assertLess(train_rows[:, 0].max(), validation_rows[:, 0].min())
        self.assertLess(validation_rows[:, 0].max(), test_rows[:, 0].min())
        indices = [set(range(len(data))[s]) for s in splits.values()]
        self.assertEqual(set.union(*indices), set(range(len(data))))
        self.assertFalse(indices[0] & indices[1] | indices[0] & indices[2] | indices[1] & indices[2])

    def test_temporal_split_requires_both_classes_in_every_period(self):
        for selected in (slice(0, 120), slice(120, 160), slice(160, 200)):
            with self.subTest(selected=selected):
                data = synthetic_rows()
                data[selected, -1] = 0
                with self.assertRaises(ValueError):
                    train.chronological_split(data)
        with self.assertRaises(ValueError):
            train.chronological_split(synthetic_rows()[::-1])
        data = synthetic_rows()
        data[:, 0] = 1
        with self.assertRaises(ValueError):
            train.chronological_split(data)

    def test_training_rejects_invalid_values_and_schema(self):
        for column, value in ((1, np.nan), (2, np.inf), (0, -1), (-2, -1), (-1, 2)):
            with self.subTest(column=column, value=value):
                rows = synthetic_rows()
                rows[0, column] = value
                write_dataset(self.dataset, rows)
                with self.assertRaises(ValueError):
                    train.load_dataset(self.dataset)
        self.dataset.write_text("Amount,Class\n1,0\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            train.load_dataset(self.dataset)

    def test_validation_threshold_and_metrics_match_known_example(self):
        labels = np.array([0, 1, 1, 0])
        scores = np.array([0.1, 0.4, 0.8, 0.9])
        threshold = train.choose_threshold(labels, scores)
        self.assertEqual(threshold, 0.4)
        report = train.evaluate(labels, scores, threshold)
        self.assertEqual(report["confusion_matrix"], [[1, 1], [0, 2]])
        self.assertAlmostEqual(report["precision"], 2 / 3)
        self.assertEqual(report["recall"], 1.0)
        self.assertAlmostEqual(report["f1"], 0.8)
        self.assertAlmostEqual(report["average_precision"], 7 / 12)
        self.assertAlmostEqual(report["roc_auc"], 0.5)
        self.assertEqual(report["accuracy"], 0.75)
        # F1=2/3 para os limiares 0.1 e 0.3: o empate prefere o menor.
        self.assertEqual(train.choose_threshold(
            np.array([1, 0, 0, 1]), np.array([0.1, 0.2, 0.25, 0.3])), 0.1)

    def test_training_cli_persists_pipeline_without_validation_or_test_leakage(self):
        raw = synthetic_rows()
        raw = np.vstack((raw, raw[[5, 125, 175]]))
        write_dataset(self.dataset, raw)
        model = self.directory / "model.pkl"
        report_path = self.directory / "metrics.json"
        examples = self.directory / "examples"
        result = subprocess.run(
            [sys.executable, str(ML_DIR / "train.py"), "--data", str(self.dataset),
             "--model", str(model), "--report", str(report_path), "--examples-dir", str(examples)],
            cwd=self.directory, capture_output=True, text=True, encoding="utf-8", errors="replace",
            timeout=60, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, "")
        artifact = joblib.load(model)
        report = json.loads(report_path.read_text(encoding="utf-8"))
        data, indices, _ = train.load_dataset(self.dataset)
        splits = train.chronological_split(data)
        training = data[splits["train"]]
        validation = data[splits["validation"]]
        testing = data[splits["test"]]
        pipeline = artifact["pipeline"]
        np.testing.assert_allclose(pipeline.named_steps["scaler"].mean_, training[:, :-1].mean(axis=0))
        self.assertEqual(int(pipeline.named_steps["scaler"].n_samples_seen_), len(training))
        expected_threshold = train.choose_threshold(
            validation[:, -1], pipeline.predict_proba(validation[:, :-1])[:, 1])
        self.assertEqual(artifact["threshold"], expected_threshold)
        self.assertEqual(report["test"], train.evaluate(
            testing[:, -1], pipeline.predict_proba(testing[:, :-1])[:, 1], expected_threshold))
        self.assertEqual(report["dataset"]["exact_duplicates_removed"], 3)
        self.assertEqual(report["model"]["sha256"], hashlib.sha256(model.read_bytes()).hexdigest())
        test_indices = set(indices[splits["test"]])
        for filename, label in (("legitima.json", 0), ("fraude.json", 1)):
            example = json.loads((examples / filename).read_text(encoding="utf-8"))
            origin = report["examples"][filename]["dataset_record_index_zero_based"]
            self.assertIn(origin, test_indices)
            self.assertEqual(raw[origin, -1], label)
            np.testing.assert_allclose(common.parse_transaction(json.dumps(example)), raw[origin, :-1])
        prediction = subprocess.run(
            [sys.executable, str(ML_DIR / "predict.py"), "--input", str(examples / "fraude.json"),
             "--model", str(model)], cwd=self.directory, text=True, encoding="utf-8",
            errors="replace", capture_output=True, timeout=30, check=False,
        )
        self.assertEqual(prediction.returncode, 0, prediction.stderr)
        self.assertEqual(prediction.stderr, "")
        self.assertEqual(len(prediction.stdout.splitlines()), 1)
        self.assertEqual(json.loads(prediction.stdout)["limiar"], artifact["threshold"])


class DatasetDownloadTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="fraud-download-tests-")
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.destination = self.directory / "creditcard.csv"
        stream = io.StringIO(newline="")
        writer = csv.writer(stream)
        writer.writerow([*common.FEATURES, common.TARGET])
        writer.writerows(synthetic_rows(10))
        self.content = stream.getvalue().encode("utf-8")
        self.checksum = hashlib.sha256(self.content).hexdigest()

    def test_valid_download_is_atomic_and_existing_file_avoids_network(self):
        with patch.object(download_data, "EXPECTED_SHA256", self.checksum), \
                patch.object(download_data, "urlopen", return_value=io.BytesIO(self.content)) as request:
            self.assertEqual(download_data.download(self.destination), self.checksum)
            self.assertEqual(self.destination.read_bytes(), self.content)
            request.assert_called_once()
            request.reset_mock()
            self.assertEqual(download_data.download(self.destination), self.checksum)
            request.assert_not_called()
        self.assertEqual(list(self.directory.glob("*.part")), [])

    def test_invalid_header_and_checksum_are_rejected_without_partial_file(self):
        for name, content, checksum in (
            ("header", b"unexpected,header\n1,2\n", None),
            ("checksum", self.content, "0" * 64),
        ):
            with self.subTest(name=name), patch.object(download_data, "EXPECTED_SHA256", checksum), \
                    patch.object(download_data, "urlopen", return_value=io.BytesIO(content)):
                with self.assertRaises(ValueError):
                    download_data.download(self.destination)
                self.assertFalse(self.destination.exists())
                self.assertEqual(list(self.directory.glob("*.part")), [])

    def test_failed_network_cleans_up_partial_download(self):
        class InterruptedResponse(io.BytesIO):
            def __init__(self, content):
                super().__init__(content)
                self.reads = 0

            def read(self, size=-1):
                self.reads += 1
                if self.reads > 1:
                    raise OSError("download interrompido")
                return super().read(20)

        with patch.object(download_data, "urlopen", return_value=InterruptedResponse(self.content)):
            with self.assertRaises(OSError):
                download_data.download(self.destination)
        self.assertFalse(self.destination.exists())
        self.assertEqual(list(self.directory.glob("*.part")), [])

    def test_corrupt_existing_file_is_preserved_and_reported(self):
        self.destination.write_bytes(self.content)
        with patch.object(download_data, "EXPECTED_SHA256", "0" * 64), \
                patch.object(download_data, "urlopen") as request:
            with self.assertRaises(ValueError):
                download_data.download(self.destination)
            request.assert_not_called()
        self.assertEqual(self.destination.read_bytes(), self.content)


if __name__ == "__main__":
    unittest.main()
