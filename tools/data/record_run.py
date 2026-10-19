"""Appends one JSON line describing this workflow run to a history file: record_run.py <record-dir> <history-file>."""
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path


def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def read_warnings(path):
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return []
    return [line[:300] for line in lines if line.strip()][:50]


def env(name):
    return os.environ.get(name) or None


def main():
    record, history = Path(sys.argv[1]), Path(sys.argv[2])
    number = env("RUN_NUMBER")
    line = {
        "run": {
            "id": env("RUN_ID"),
            "number": int(number) if number else None,
            "attempt": env("RUN_ATTEMPT"),
            "event": env("EVENT"),
            "sha": env("SHA"),
            "recorded": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "url": f"{env('SERVER_URL')}/{env('REPOSITORY')}/actions/runs/{env('RUN_ID')}" if env("RUN_ID") else None,
        },
        "result": {"build": env("BUILD_RESULT"), "deploy": env("DEPLOY_RESULT")},
        "steps": {"pois": env("POIS_OUTCOME"), "validate": env("VALIDATE_OUTCOME"), "places": env("PLACES_OUTCOME")},
        "pois": read_json(record / "pois.json"),
        "places": read_json(record / "places.json"),
        "warnings": read_warnings(record / "warnings.log"),
    }
    with history.open("a", encoding="utf-8") as f:
        f.write(json.dumps(line, separators=(",", ":"), ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
