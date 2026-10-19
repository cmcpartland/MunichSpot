#!/usr/bin/env python3
"""Checks a places.json for plausibility. Exits non-zero on any error."""
import json
import math
import sys
from collections import Counter
from datetime import datetime

from ci import warn

KINDS = {"street", "park", "square", "station", "landmark", "district"}
MIN_PLACES = 5000
LAT_RANGE = (47.9, 48.4)
LON_RANGE = (11.2, 11.9)


def check(path):
    errors = []
    try:
        with open(path, encoding="utf-8") as f:
            root = json.load(f)
    except (OSError, ValueError) as e:
        return [f"can't read {path}: {e}"]
    if not isinstance(root, dict):
        return ["top level is not an object"]
    try:
        datetime.fromisoformat(root["generated"].replace("Z", "+00:00"))
    except (KeyError, AttributeError, ValueError):
        errors.append("missing or unparseable 'generated'")
    places = root.get("places")
    if not isinstance(places, list):
        return errors + ["'places' is not an array"]
    if len(places) < MIN_PLACES:
        errors.append(f"only {len(places)} places, need at least {MIN_PLACES}")
    kinds = Counter()
    bad = 0
    for p in places:
        if not (isinstance(p, list) and len(p) == 4 and isinstance(p[0], str) and p[0].strip() and isinstance(p[1], str)
                and all(isinstance(v, (int, float)) and math.isfinite(v) for v in p[2:])
                and LAT_RANGE[0] <= p[2] <= LAT_RANGE[1] and LON_RANGE[0] <= p[3] <= LON_RANGE[1]):
            bad += 1
            if bad <= 5:
                errors.append(f"bad entry {p!r}")
            continue
        kinds[p[1]] += 1
    if bad > 5:
        errors.append(f"{bad} bad entries in total")
    for kind in sorted(KINDS - set(kinds)):
        errors.append(f"no places of kind {kind}")
    for kind in sorted(set(kinds) - KINDS):
        errors.append(f"unknown kind {kind}")
    print(f"{len(places)} places: {dict(kinds)}")
    return errors


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: validate_places.py places.json")
    errors = check(sys.argv[1])
    for e in errors:
        warn(f"places validation: {e}")
    sys.exit(1 if errors else 0)


main()
