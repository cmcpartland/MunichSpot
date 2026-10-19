#!/usr/bin/env python3
"""Checks a pois.geojson for plausibility, optionally against the last deployed file. Exits non-zero on any error."""
import argparse
import json
import math
import os
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path

import requests

USER_AGENT = "MunichSpot-data/0.1 (+https://github.com/cmcpartland/MunichSpot)"

ID_PREFIX = {"RECYCLING": "awm-", "POST_BOX": "osm-", "DRINKING_WATER": "tb-", "TOILET": "wc-"}
MATERIALS = {"GLASS_BROWN", "GLASS_GREEN", "GLASS_WHITE", "PACKAGING", "CLOTHING"}
MIN_PER_TYPE = {"RECYCLING": 500, "POST_BOX": 500, "DRINKING_WATER": 50, "TOILET": 100}
MIN_TOTAL = 1000
LAT_RANGE = (47.9, 48.4)
LON_RANGE = (11.2, 11.9)

MIN_DISTINCT_COORDS = 0.9
MIN_RECYCLING_WITH_MATERIALS = 0.9
MIN_CLOTHING = 100
MIN_POST_BOX_WITH_TIMES = 0.8
MIN_TOILET_WITH_HOURS = 0.5
MIN_HOURS_TRANSLATED = 0.9
MAX_TOILET_BAD_STATUS = 0.5

MIN_PREVIOUS_RATIO = 0.8
MAX_PREVIOUS_RATIO = 1.5
MIN_ID_OVERLAP = 0.7
FETCH_TIMEOUT = 60


def props(f):
    return f.get("properties") or {}


def pct(part, whole):
    return f"{round(100 * part / whole)}%" if whole else "n/a"


def counts(features):
    c = {t: sum(props(f).get("type") == t for f in features) for t in ID_PREFIX}
    c["CLOTHING"] = sum("CLOTHING" in (props(f).get("materials") or ()) for f in features)
    return c


def check_generated(value):
    if not isinstance(value, str) or not value:
        return ["generated: missing"]
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return [f"generated: {value!r} is not an ISO 8601 timestamp"]
    return []


def check_structure(features, errors):
    seen = set()
    for i, f in enumerate(features):
        if not isinstance(f, dict):
            errors.append(f"feature {i}: not an object")
            continue
        id_, p = f.get("id"), props(f)
        t = p.get("type")
        if not isinstance(id_, str) or not id_:
            errors.append(f"feature {i}: missing or non-string id")
        elif id_ in seen:
            errors.append(f"{id_}: duplicate id")
        else:
            seen.add(id_)
        if t not in ID_PREFIX:
            errors.append(f"{id_}: unknown type {t!r}")
        elif isinstance(id_, str) and not id_.startswith(ID_PREFIX[t]):
            errors.append(f"{id_}: id of a {t} should start with {ID_PREFIX[t]!r}")
        if not isinstance(p.get("name"), str):
            errors.append(f"{id_}: missing name")
        bad = set(p.get("materials") or ()) - MATERIALS
        if bad:
            errors.append(f"{id_}: unknown materials {sorted(bad)}")
        g = f.get("geometry") or {}
        c = g.get("coordinates")
        ok = (
            g.get("type") == "Point"
            and isinstance(c, list)
            and len(c) == 2
            and all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) for v in c)
        )
        if not ok:
            errors.append(f"{id_}: geometry is not a Point with two finite numbers")
        elif not (LAT_RANGE[0] <= c[1] <= LAT_RANGE[1] and LON_RANGE[0] <= c[0] <= LON_RANGE[1]):
            errors.append(f"{id_} outside Munich: {c[1]}, {c[0]}")


def check_content(features, c, errors, warnings):
    if len(features) < MIN_TOTAL:
        errors.append(f"only {len(features)} features in total (min {MIN_TOTAL})")
    for t, minimum in MIN_PER_TYPE.items():
        if c[t] < minimum:
            errors.append(f"{t}: only {c[t]} features (min {minimum})")
    by_type = {t: [f for f in features if isinstance(f, dict) and props(f).get("type") == t] for t in ID_PREFIX}
    for t, fs in by_type.items():
        coords = {tuple(f["geometry"]["coordinates"]) for f in fs if isinstance(f.get("geometry"), dict) and isinstance(f["geometry"].get("coordinates"), list)}
        if fs and len(coords) < MIN_DISTINCT_COORDS * len(fs):
            errors.append(f"{t}: only {len(coords)} distinct coordinates in {len(fs)} features (min {pct(MIN_DISTINCT_COORDS, 1)})")

    def share(t, pred, minimum, what):
        n, total = sum(pred(props(f)) for f in by_type[t]), len(by_type[t])
        if total and n < minimum * total:
            errors.append(f"{t}: only {n} of {total} features ({pct(n, total)}) {what} (min {pct(minimum, 1)})")

    share("RECYCLING", lambda p: bool(p.get("materials")), MIN_RECYCLING_WITH_MATERIALS, "have materials")
    if c["CLOTHING"] < MIN_CLOTHING:
        errors.append(f"RECYCLING: only {c['CLOTHING']} islands with CLOTHING (min {MIN_CLOTHING}), the Altkleider category would vanish")
    share("POST_BOX", lambda p: bool(p.get("collectionTimes")), MIN_POST_BOX_WITH_TIMES, "have collectionTimes")
    share("TOILET", lambda p: bool(p.get("hours")), MIN_TOILET_WITH_HOURS, "have hours")
    toilets = by_type["TOILET"]
    with_hours = [f for f in toilets if props(f).get("hours")]
    translated = sum(bool(props(f).get("hoursEn")) for f in with_hours)
    if with_hours and translated < MIN_HOURS_TRANSLATED * len(with_hours):
        warnings.append(f"TOILET: only {translated} of {len(with_hours)} ({pct(translated, len(with_hours))}) hours have hoursEn (min {pct(MIN_HOURS_TRANSLATED, 1)})")
    bad = sum(props(f).get("status") in ("DEFECT", "IMPAIRED") for f in toilets)
    if toilets and bad > MAX_TOILET_BAD_STATUS * len(toilets):
        errors.append(f"TOILET: {bad} of {len(toilets)} ({pct(bad, len(toilets))}) are DEFECT/IMPAIRED (max {pct(MAX_TOILET_BAD_STATUS, 1)})")


def check_previous(features, previous, c, pc, errors, warnings):
    for key, label in (*((t, "features") for t in ID_PREFIX), ("CLOTHING", "islands")):
        if not pc[key]:
            continue
        ratio = c[key] / pc[key]
        if ratio < MIN_PREVIOUS_RATIO:
            errors.append(f"{key}: {c[key]} {label}, {pct(c[key], pc[key])} of previous {pc[key]} (min {pct(MIN_PREVIOUS_RATIO, 1)})")
        elif ratio > MAX_PREVIOUS_RATIO and key != "CLOTHING":
            errors.append(f"{key}: {c[key]} {label}, {pct(c[key], pc[key])} of previous {pc[key]} (max {pct(MAX_PREVIOUS_RATIO, 1)})")
    for t in ID_PREFIX:
        old = {f.get("id") for f in previous if isinstance(f, dict) and props(f).get("type") == t}
        new = {f.get("id") for f in features if isinstance(f, dict) and props(f).get("type") == t}
        if old and len(old & new) < MIN_ID_OVERLAP * len(old):
            warnings.append(f"{t}: only {len(old & new)} of {len(old)} previous ids kept ({pct(len(old & new), len(old))}, min {pct(MIN_ID_OVERLAP, 1)}); ids may have been renumbered")


def validate(features, previous=None):
    """Returns (errors, warnings). `previous` is the feature list of the last good file, or None to skip the comparison."""
    errors, warnings = [], []
    if not isinstance(features, list):
        return ["features is not a list"], warnings
    check_structure(features, errors)
    c = counts([f for f in features if isinstance(f, dict)])
    check_content([f for f in features if isinstance(f, dict)], c, errors, warnings)
    if previous is not None:
        check_previous(features, previous, c, counts([f for f in previous if isinstance(f, dict)]), errors, warnings)
    return errors, warnings


def summary_rows(features, previous):
    c = counts([f for f in features if isinstance(f, dict)])
    pc = counts([f for f in previous if isinstance(f, dict)]) if previous is not None else None
    rows = []
    for key, n in c.items():
        if pc is None:
            rows.append((key, "-", str(n), "-"))
        else:
            rows.append((key, str(pc[key]), str(n), f"{n - pc[key]:+d} ({pct(n - pc[key], pc[key]) if pc[key] else 'n/a'})"))
    return rows


def print_summary(rows):
    head = ("type", "previous", "new", "delta")
    print("\n".join("  ".join(v.ljust(14) for v in r).rstrip() for r in (head, *rows)))
    if path := os.environ.get("GITHUB_STEP_SUMMARY"):
        lines = ["### pois.geojson: previous vs new", "", "| type | previous | new | delta |", "|---|---:|---:|---:|"]
        lines += [f"| {' | '.join(r)} |" for r in rows]
        with open(path, "a", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n\n")


def load_previous(source):
    """Returns the previous feature list, or None (with a warning printed) if it can't be read."""
    try:
        if source.startswith(("http://", "https://")):
            resp = requests.get(source, headers={"User-Agent": USER_AGENT}, timeout=FETCH_TIMEOUT)
            resp.raise_for_status()
            data = resp.json()
        else:
            data = json.loads(Path(source).read_text(encoding="utf-8"))
        features = data["features"]
        if not isinstance(features, list):
            raise ValueError("features is not a list")
        return features
    except Exception as e:
        print(f"WARNING: previous file {source} unusable, skipping comparison: {e}")
        return None


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("path", type=Path)
    ap.add_argument("--previous", help="URL or path of the last good pois.geojson to compare against")
    ap.add_argument("--skip-previous", action="store_true", help="skip the comparison with --previous")
    args = ap.parse_args()
    try:
        data = json.loads(args.path.read_text(encoding="utf-8"))
        if data.get("type") != "FeatureCollection":
            raise ValueError(f"type is {data.get('type')!r}")
        features = data["features"]
    except Exception as e:
        sys.exit(f"validation failed: {args.path} is not a readable FeatureCollection: {e}")
    previous = load_previous(args.previous) if args.previous and not args.skip_previous else None
    errors, warnings = validate(features, previous)
    errors.extend(check_generated(data.get("generated")))
    for w in warnings:
        print(f"WARNING: {w}")
    if isinstance(features, list):
        print_summary(summary_rows(features, previous))
    if errors:
        shown = errors[:50]
        more = f"\n... and {len(errors) - 50} more" if len(errors) > 50 else ""
        sys.exit(f"validation failed ({len(errors)} errors):\n" + "\n".join(shown) + more)
    print("validation passed")


if __name__ == "__main__":
    main()
