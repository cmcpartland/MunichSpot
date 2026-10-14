"""Builds munich_boundary.geojson: a world polygon with Munich's city boundary (OSM relation 62428) as a hole."""
import json
import sys
from pathlib import Path

import requests

SERVERS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"]
QUERY = "[out:json][timeout:60];relation(62428);out geom;"
HEADERS = {"User-Agent": "MunichSpot-data/1.0 (https://github.com/cmcpartland/MunichSpot)"}
TOLERANCE = 0.0002
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "shared/src/commonMain/composeResources/files/munich_boundary.geojson"


def fetch():
    for url in SERVERS:
        try:
            r = requests.post(url, data={"data": QUERY}, headers=HEADERS, timeout=90)
            r.raise_for_status()
            return r.json()["elements"][0]
        except Exception as e:
            print(f"{url} failed: {e}", file=sys.stderr)
    sys.exit("all Overpass servers failed")


def join_ways(ways):
    rings = []
    while ways:
        ring = ways.pop(0)
        while ring[0] != ring[-1]:
            for i, w in enumerate(ways):
                if w[0] == ring[-1]:
                    ring += w[1:]
                elif w[-1] == ring[-1]:
                    ring += w[::-1][1:]
                elif w[-1] == ring[0]:
                    ring = w[:-1] + ring
                elif w[0] == ring[0]:
                    ring = w[::-1][:-1] + ring
                else:
                    continue
                del ways[i]
                break
            else:
                sys.exit("boundary ways do not join into closed rings")
        rings.append(ring)
    return rings


def dp(pts, tol):
    if len(pts) < 3:
        return pts
    (x1, y1), (x2, y2) = pts[0], pts[-1]
    dx, dy = x2 - x1, y2 - y1
    norm = (dx * dx + dy * dy) ** 0.5
    best, idx = -1.0, 0
    for i in range(1, len(pts) - 1):
        x, y = pts[i]
        d = abs(dy * (x - x1) - dx * (y - y1)) / norm if norm else ((x - x1) ** 2 + (y - y1) ** 2) ** 0.5
        if d > best:
            best, idx = d, i
    if best <= tol:
        return [pts[0], pts[-1]]
    return dp(pts[: idx + 1], tol)[:-1] + dp(pts[idx:], tol)


def area(ring):
    return sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(ring, ring[1:])) / 2


def main():
    rel = fetch()
    ways = [[(p["lon"], p["lat"]) for p in m["geometry"]] for m in rel["members"] if m["type"] == "way" and m["role"] == "outer"]
    rings = []
    for r in join_ways(ways):
        r = [(round(x, 5), round(y, 5)) for x, y in dp(r, TOLERANCE)]
        rings.append(r[::-1] if area(r) > 0 else r)  # holes must be clockwise
    rings.sort(key=lambda r: area(r))  # most negative (largest) first
    ring = rings[0]
    xs, ys = [p[0] for p in ring], [p[1] for p in ring]
    if len(ring) < 100 or not (11.3 < min(xs) < 11.4 and 11.7 < max(xs) < 11.75 and 48.05 < min(ys) < 48.1 and 48.2 < max(ys) < 48.27):
        sys.exit(f"boundary failed sanity check: {len(ring)} points, lon {min(xs)}..{max(xs)}, lat {min(ys)}..{max(ys)}")
    world = [[-180, -85], [180, -85], [180, 85], [-180, 85], [-180, -85]]
    feature = {"type": "Feature", "properties": {}, "geometry": {"type": "Polygon", "coordinates": [world] + [[list(p) for p in r] for r in rings if len(r) >= 4]}}
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    out.write_text(json.dumps(feature, separators=(",", ":")))


main()
