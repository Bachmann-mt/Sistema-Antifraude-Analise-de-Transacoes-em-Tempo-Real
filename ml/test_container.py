"""Verifica imagens Docker já construídas e registra evidências reais em JSON.

Usa somente a biblioteca padrão. Não constrói imagens, baixa imagens ou monta
diretórios do host; todos os processos de inferência rodam sem acesso à rede.
"""

import argparse
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

from common import ML_DIR, MODEL_VERSION, SCHEMA_VERSION

CONTAINER_PYTHON = "/opt/venv/bin/python"
CONTAINER_PREDICT = "/app/ml/predict.py"
MAX_RECORDED_OUTPUT = 16000

# Inspeciona o runtime real, sem iniciar o servidor Java nem carregar dados de treino.
INTROSPECTION = """
import hashlib, importlib.metadata, json, os, pathlib, platform
import joblib
base = pathlib.Path('/app/ml')
model_path = base / 'models/fraud_model.pkl'
artifact = joblib.load(model_path)
training_report = json.loads((base / 'reports/metrics.json').read_text(encoding='utf-8'))
forbidden = [base / 'train.py', base / 'download_data.py', base / '.venv', pathlib.Path('/app/.venv')]
print(json.dumps({
    'uid': os.getuid(), 'gid': os.getgid(), 'python': platform.python_version(),
    'versions': {name: importlib.metadata.version(name) for name in
                 ['numpy', 'scipy', 'scikit-learn', 'joblib', 'threadpoolctl']},
    'model_path': str(model_path), 'model_bytes': model_path.stat().st_size,
    'model_sha256': hashlib.sha256(model_path.read_bytes()).hexdigest(),
    'training_report_model_sha256': training_report['model']['sha256'],
    'model_schema_version': artifact.get('schema_version'),
    'model_version': artifact.get('model_version'),
    'model_sklearn_version': artifact.get('sklearn_version'),
    'schemas': {name: (base / 'contracts' / name).is_file() for name in
                ['input.schema.json', 'output.schema.json', 'error.schema.json']},
    'forbidden_files_present': [str(path) for path in forbidden if path.exists()],
    'csv_files_present': [str(path) for path in base.rglob('*.csv')]
}))
""".strip()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def strict_json(text):
    def invalid_constant(value):
        raise ValueError(f"Constante JSON inválida: {value}")
    return json.loads(text, parse_constant=invalid_constant)


def successful_prediction(result):
    require(result.returncode == 0, "Inferência retornou código diferente de zero.")
    require(result.stderr == "", "Inferência válida produziu stderr.")
    require(len(result.stdout.splitlines()) == 1, "stdout deve conter exatamente uma linha JSON.")
    value = strict_json(result.stdout)
    require(isinstance(value, dict), "A saída precisa ser um objeto JSON.")
    require(set(value) == {"status", "score_fraude", "limiar", "modelo"}, "Campos de saída incorretos.")
    for field in ("score_fraude", "limiar"):
        number = value[field]
        require(not isinstance(number, bool) and isinstance(number, (int, float))
                and math.isfinite(number) and 0 <= number <= 1, f"{field} fora do contrato.")
    require(value["modelo"] == MODEL_VERSION, "Versão do modelo inesperada.")
    expected = "FRAUDE" if value["score_fraude"] >= value["limiar"] else "APROVADO"
    require(value["status"] == expected, "Status inconsistente com score e limiar.")
    return value


def error_prediction(result, exit_code):
    require(result.returncode == exit_code, f"Erro deve retornar exit code {exit_code}.")
    require(result.stdout == "", "Uma falha contaminou stdout.")
    require(len(result.stderr.splitlines()) == 1, "Erro deve ser uma única linha JSON em stderr.")
    value = strict_json(result.stderr)
    require(isinstance(value, dict) and set(value) == {"erro"}, "Objeto de erro fora do contrato.")
    require(isinstance(value["erro"], str) and bool(value["erro"].strip()), "Mensagem de erro vazia.")
    require("Traceback" not in result.stderr, "Erro expôs traceback.")
    return value


def help_result(result):
    require(result.returncode == 0, "Ajuda deve retornar sucesso.")
    require(result.stdout == "", "Ajuda deve preservar stdout vazio.")
    require("--input" in result.stderr and "--model" in result.stderr, "Ajuda ausente em stderr.")


def pinned_versions():
    versions = {}
    for line in (ML_DIR / "requirements.txt").read_text(encoding="utf-8-sig").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            package, version = line.split("==", 1)
            versions[package] = version
    return versions


def introspection_result(result, expected_versions):
    require(result.returncode == 0, "Introspecção do runtime falhou.")
    require(result.stderr == "", "Introspecção produziu stderr.")
    value = strict_json(result.stdout)
    require(isinstance(value.get("uid"), int) and value["uid"] > 0, "Container está rodando como root.")
    require(value["versions"] == expected_versions, "Dependências diferem de requirements.txt.")
    require(value["model_bytes"] > 0, "Modelo vazio.")
    require(bool(re.fullmatch(r"[0-9a-f]{64}", value["model_sha256"])), "Hash do modelo inválido.")
    require(value["model_sha256"] == value["training_report_model_sha256"],
            "Hash do modelo difere do relatório produzido no build.")
    require(value["model_schema_version"] == SCHEMA_VERSION, "Schema do artefato incompatível.")
    require(value["model_version"] == MODEL_VERSION, "Versão do artefato incompatível.")
    require(value["model_sklearn_version"] == expected_versions["scikit-learn"],
            "Modelo foi treinado com outra versão do scikit-learn.")
    require(all(value["schemas"].values()) and len(value["schemas"]) == 3, "Schemas ausentes da imagem.")
    require(not value["forbidden_files_present"], "Imagem inclui scripts de treino ou venv do host.")
    require(not value["csv_files_present"], "Imagem final inclui CSV de treinamento.")
    return value


class Runner:
    def __init__(self):
        self.records = []

    def run(self, name, command, validator, timeout=60, container_name=None):
        started = time.monotonic()
        record = {"name": name, "command": command, "executed": False, "passed": False}
        result = None
        value = None
        try:
            result = subprocess.run(command, shell=False, capture_output=True, text=True,
                                    encoding="utf-8", errors="replace", timeout=timeout, check=False)
            record.update(executed=True, exit_code=result.returncode, timed_out=False)
            value = validator(result)
            record["passed"] = True
        except subprocess.TimeoutExpired as error:
            record.update(executed=True, exit_code=None, timed_out=True,
                          failure=f"Tempo limite de {timeout}s excedido.")
            stdout = error.stdout or ""
            stderr = error.stderr or ""
            result = subprocess.CompletedProcess(command, None,
                stdout.decode("utf-8", errors="replace") if isinstance(stdout, bytes) else stdout,
                stderr.decode("utf-8", errors="replace") if isinstance(stderr, bytes) else stderr)
        except OSError as error:
            record.update(exit_code=None, timed_out=False, failure=str(error))
        except (ValueError, TypeError, KeyError) as error:
            record["failure"] = str(error)
        record["duration_seconds"] = round(time.monotonic() - started, 3)
        for channel in ("stdout", "stderr"):
            output = getattr(result, channel, "") if result is not None else ""
            record[channel] = output[:MAX_RECORDED_OUTPUT]
            record[channel + "_characters"] = len(output)
            record[channel + "_truncated"] = len(output) > MAX_RECORDED_OUTPUT
        if value is not None:
            record["result"] = value
        self.records.append(record)
        print(f"{'PASSOU' if record['passed'] else 'FALHOU'}: {name}", flush=True)
        if record.get("timed_out") and container_name:
            self.run(name + ":cleanup", ["docker", "rm", "--force", container_name],
                     lambda cleanup: require(cleanup.returncode == 0, "Falha ao remover container após timeout."),
                     timeout=15)
        return value if record["passed"] else None

    def container(self, name, image_id, arguments, validator, entrypoint=None):
        container_name = "antifraude-check-" + uuid.uuid4().hex[:12]
        command = ["docker", "run", "--rm", "--pull=never", "--name", container_name,
                   "--network", "none", "--read-only", "--cap-drop", "ALL",
                   "--security-opt", "no-new-privileges"]
        if entrypoint:
            command += ["--entrypoint", entrypoint]
        command += [image_id, *arguments]
        return self.run(name, command, validator, container_name=container_name)


def inspect_image(result):
    require(result.returncode == 0, "Imagem local indisponível; construa a imagem antes dos testes.")
    identifier = result.stdout.strip()
    require(bool(re.fullmatch(r"sha256:[0-9a-f]{64}", identifier)), "Docker não retornou um image ID válido.")
    return identifier


def check_image(runner, image, payload, versions, api=False):
    image_id = runner.run(image + ":inspect", ["docker", "image", "inspect", "--format", "{{.Id}}", image],
                          inspect_image, timeout=30)
    summary = {"image": image, "kind": "api" if api else "ml-inference", "image_id": image_id}
    if image_id is None:
        return summary
    summary["runtime"] = runner.container(image + ":runtime", image_id, ["-c", INTROSPECTION],
        lambda result: introspection_result(result, versions), entrypoint=CONTAINER_PYTHON)
    if api:
        def java_version(result):
            require(result.returncode == 0, "Java não está disponível na imagem da API.")
            require("version" in result.stderr.lower() or "version" in result.stdout.lower(),
                    "java -version não retornou uma versão.")
        runner.container(image + ":java-version", image_id, ["-version"], java_version, entrypoint="java")

    def infer(name, arguments, validator=successful_prediction):
        prefix = [CONTAINER_PREDICT] if api else []
        return runner.container(image + ":" + name, image_id, [*prefix, *arguments], validator,
                                entrypoint=CONTAINER_PYTHON if api else None)

    file_result = infer("arquivo-legitima", ["--input", "/app/ml/examples/legitima.json"])
    infer("arquivo-fraude", ["--input", "/app/ml/examples/fraude.json"])
    positional = infer("json-posicional", [json.dumps(payload, allow_nan=False)])

    def equivalent(result):
        value = successful_prediction(result)
        require(positional is not None, "Predição de referência falhou.")
        require(value == positional, "Ordem das chaves alterou o resultado.")
        require(file_result is not None and value == file_result, "Arquivo e JSON local não produziram o mesmo resultado.")
        return value
    infer("json-chaves-invertidas", [json.dumps(dict(reversed(list(payload.items()))))], equivalent)

    missing = dict(payload)
    missing.pop("V28")
    invalid_inputs = {
        "campo-ausente": json.dumps(missing),
        "campo-extra": json.dumps({**payload, "Class": 0}),
        "booleano": json.dumps({**payload, "V1": True}),
        "nao-finito": json.dumps({**payload, "V1": float("nan")}),
        "json-malformado": "{",
    }
    for name, value in invalid_inputs.items():
        infer(name, [value], lambda result: error_prediction(result, 2))
    infer("opcao-desconhecida", ["--opcao-inexistente"], lambda result: error_prediction(result, 2))
    infer("entrada-ausente", [], lambda result: error_prediction(result, 2))
    infer("modelo-ausente", [json.dumps(payload), "--model", "/app/ml/models/ausente.pkl"],
          lambda result: error_prediction(result, 1))
    infer("overflow-na-inferencia", [json.dumps({**payload, "V28": sys.float_info.max})],
          lambda result: error_prediction(result, 1))
    infer("ajuda", ["--help"], help_result)
    return summary


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", default="antifraude-ml:local", help="Imagem local do target ml-inference.")
    parser.add_argument("--api-image", help="Opcional: imagem local da API Java com Python integrado.")
    parser.add_argument("--report", type=Path, default=ML_DIR / "reports/container-tests.json")
    args = parser.parse_args(argv)
    runner = Runner()
    report = {"started_at_utc": datetime.now(timezone.utc).isoformat(), "images": [], "checks": runner.records}
    try:
        payload = strict_json((ML_DIR / "examples/legitima.json").read_text(encoding="utf-8-sig"))
        versions = pinned_versions()
        report["expected_versions"] = versions
        report["images"].append(check_image(runner, args.image, payload, versions))
        if args.api_image:
            report["images"].append(check_image(runner, args.api_image, payload, versions, api=True))
    except (OSError, ValueError, TypeError, KeyError) as error:
        report["setup_error"] = str(error)
    report["finished_at_utc"] = datetime.now(timezone.utc).isoformat()
    report["passed"] = (bool(runner.records) and not report.get("setup_error")
                        and all(record["passed"] for record in runner.records))
    report["passed_checks"] = sum(record["passed"] for record in runner.records)
    report["total_checks"] = len(runner.records)
    try:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n",
                               encoding="utf-8")
    except OSError as error:
        print(f"Não foi possível gravar o relatório: {error}", file=sys.stderr)
        return 1
    print(f"{report['passed_checks']}/{report['total_checks']} verificações passaram. Relatório: {args.report}")
    if report.get("setup_error"):
        print(report["setup_error"], file=sys.stderr)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
