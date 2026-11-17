"""Confere schemas publicados e a disciplina stdout/stderr da interface CLI."""

import json
from pathlib import Path
import subprocess
import sys
import unittest

ML_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ML_DIR))
from common import FEATURES, parse_transaction


class PublishedSchemaTests(unittest.TestCase):
    def load_schema(self, name):
        value = json.loads((ML_DIR / "contracts" / f"{name}.schema.json").read_text(encoding="utf-8"))
        self.assertEqual(value["$schema"], "https://json-schema.org/draft/2020-12/schema")
        self.assertEqual(value["type"], "object")
        self.assertFalse(value["additionalProperties"])
        self.assertEqual(set(value["required"]), set(value["properties"]))
        return value

    def test_input_schema_matches_the_exact_model_contract(self):
        schema = self.load_schema("input")
        self.assertEqual(set(schema["required"]), set(FEATURES))
        self.assertEqual(len(schema["required"]), len(FEATURES))
        for name in FEATURES:
            with self.subTest(feature=name):
                self.assertEqual(schema["properties"][name]["type"], "number")
        for name in ("Time", "Amount"):
            self.assertEqual(schema["properties"][name]["minimum"], 0)
        for name in FEATURES[1:-1]:
            self.assertNotIn("minimum", schema["properties"][name])

    def test_output_schema_has_status_enum_and_score_bounds(self):
        schema = self.load_schema("output")
        self.assertEqual(set(schema["properties"]), {"status", "score_fraude", "limiar", "modelo"})
        self.assertEqual(set(schema["properties"]["status"]["enum"]), {"APROVADO", "FRAUDE"})
        for name in ("score_fraude", "limiar"):
            field = schema["properties"][name]
            self.assertEqual(field["type"], "number")
            self.assertEqual(field["minimum"], 0)
            self.assertEqual(field["maximum"], 1)
        self.assertEqual(schema["properties"]["modelo"]["type"], "string")
        self.assertEqual(schema["properties"]["modelo"]["minLength"], 1)

    def test_error_schema_has_only_a_nonempty_message(self):
        schema = self.load_schema("error")
        self.assertEqual(schema["required"], ["erro"])
        self.assertEqual(schema["properties"]["erro"]["type"], "string")
        self.assertEqual(schema["properties"]["erro"]["minLength"], 1)

    def test_examples_obey_input_contract_regardless_of_key_order(self):
        schema = self.load_schema("input")
        for filename in ("legitima.json", "fraude.json"):
            with self.subTest(filename=filename):
                text = (ML_DIR / "examples" / filename).read_text(encoding="utf-8-sig")
                payload = json.loads(text)
                values = parse_transaction(text)
                self.assertEqual(set(payload), set(schema["properties"]))
                self.assertEqual(len(values), len(FEATURES))
                self.assertEqual(values, parse_transaction(json.dumps(dict(reversed(list(payload.items()))))))


class CleanCliTests(unittest.TestCase):
    def invoke(self, *arguments):
        return subprocess.run([sys.executable, str(ML_DIR / "predict.py"), *arguments],
                              capture_output=True, text=True, encoding="utf-8", errors="replace",
                              timeout=15, check=False)

    def assert_json_error(self, result):
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(len(result.stderr.splitlines()), 1)
        value = json.loads(result.stderr)
        self.assertEqual(set(value), {"erro"})
        self.assertIsInstance(value["erro"], str)
        self.assertTrue(value["erro"].strip())
        self.assertNotIn("Traceback", result.stderr)

    def test_help_uses_stderr_and_preserves_stdout_for_predictions(self):
        for flag in ("-h", "--help"):
            with self.subTest(flag=flag):
                result = self.invoke(flag)
                self.assertEqual(result.returncode, 0)
                self.assertEqual(result.stdout, "")
                self.assertIn("--input", result.stderr)
                self.assertIn("--model", result.stderr)

    def test_argument_parser_failures_are_json(self):
        for arguments in ((), ("--unknown",), ("--input",),
                          ("{}", "--input", "irrelevant.json"), ("{}", "{}")):
            with self.subTest(arguments=arguments):
                self.assert_json_error(self.invoke(*arguments))

    def test_large_array_and_malformed_json_fail_without_traceback(self):
        # Fica abaixo do limite de linha de comando do Windows.
        for value in ("[" + "0," * 3000 + "0]", "[" + "0," * 3000 + "}",
                      "[" * 2000 + "0" + "]" * 2000):
            with self.subTest(length=len(value), ending=value[-8:]):
                self.assert_json_error(self.invoke(value))


if __name__ == "__main__":
    unittest.main()
