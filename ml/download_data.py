"""Baixa o CSV ULB/Worldline do espelho utilizado pelo tutorial do TensorFlow."""

import argparse
import csv
import hashlib
from pathlib import Path
import sys
import tempfile
from urllib.request import urlopen

from common import DATASET_URL, DEFAULT_DATA, FEATURES, TARGET

# Preenchido com o SHA-256 verificado da distribuição pública.
EXPECTED_SHA256 = "76274b691b16a6c49d3f159c883398e03ccd6d1ee12d9d8ee38f4b4b98551a89"


def validate_file(path):
    with path.open("r", encoding="utf-8-sig", newline="") as source:
        header = next(csv.reader(source), None)
    if header != [*FEATURES, TARGET]:
        raise ValueError("Arquivo recebido não tem o cabeçalho esperado do creditcard.csv.")
    with path.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    if digest != EXPECTED_SHA256:
        raise ValueError("SHA-256 diferente da versão esperada; download recusado.")
    return digest


def download(destination):
    """Reutiliza uma cópia íntegra; instala downloads somente depois de validar."""
    if destination.exists():
        return validate_file(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=destination.parent, suffix=".part", delete=False) as target:
            temporary = Path(target.name)
            with urlopen(DATASET_URL, timeout=60) as response:
                while chunk := response.read(1024 * 1024):
                    target.write(chunk)
        digest = validate_file(temporary)
        temporary.replace(destination)
        return digest
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_DATA)
    args = parser.parse_args(argv)
    try:
        print(f"Dataset: {args.output}", flush=True)
        digest = download(args.output)
    except Exception as exc:
        print(f"Erro no download: {exc}", file=sys.stderr)
        return 1
    print(f"CSV validado. SHA-256: {digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
