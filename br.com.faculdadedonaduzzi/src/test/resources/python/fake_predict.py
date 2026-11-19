import argparse
import json
import sys
import time
from pathlib import Path


parser = argparse.ArgumentParser()
parser.add_argument("--input", required=True)
parser.add_argument("--model", required=True)
args = parser.parse_args()

if args.model == "timeout":
    time.sleep(30)
elif args.model in ("exit-1", "exit-2"):
    print("erro deterministico da fixture", file=sys.stderr)
    raise SystemExit(int(args.model[-1]))
else:
    entrada = json.loads(Path(args.input).read_text(encoding="utf-8"))
    campos = {"Time", "Amount", *(f"V{i}" for i in range(1, 29))}
    if set(entrada) != campos or entrada["Time"] not in (0.0, 146022.0) or entrada["Amount"] != 1.18:
        print("entrada fora do contrato", file=sys.stderr)
        raise SystemExit(2)
    if entrada["Time"] == 0.0:
        print("falha deterministica da fixture", file=sys.stderr)
        raise SystemExit(1)

    saidas = {
        "success": '{"status":"FRAUDE","score_fraude":0.9999999999964237,"limiar":0.9999922476121025,"modelo":"fixture-v1"}',
        "invalid-json": "isto nao e JSON",
        "missing-field": '{"status":"FRAUDE","score_fraude":0.9,"limiar":0.8}',
        "inconsistent": '{"status":"APROVADO","score_fraude":0.9,"limiar":0.8,"modelo":"fixture-v1"}',
        "out-of-range": '{"status":"FRAUDE","score_fraude":1.1,"limiar":0.8,"modelo":"fixture-v1"}',
        "extra-field": '{"status":"FRAUDE","score_fraude":0.9,"limiar":0.8,"modelo":"fixture-v1","debug":true}',
    }
    print(saidas.get(args.model, saidas["success"]))