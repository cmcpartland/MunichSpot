#!/usr/bin/env python3
"""Builds places.json: {"generated": UTC timestamp, "places": [[name, kind, lat, lon], ...]} of named Munich streets, parks, squares, stations, landmarks and districts."""
import json
import math
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from build_pois import OVERPASS_URL, fetch

QUERY = """[out:json][timeout:180];
area["boundary"="administrative"]["admin_level"="6"]["name"="München"]["de:amtlicher_gemeindeschluessel"="09162000"]->.a;
(
way["highway"~"^(primary|secondary|tertiary|unclassified|residential|living_street|pedestrian)$"][name](area.a);
nwr["leisure"="park"][name](area.a);
nwr["place"="square"][name](area.a);
nwr["railway"="station"][name](area.a);
nwr["tourism"~"^(attraction|museum|zoo)$"][name](area.a);
nwr["leisure"="stadium"][name](area.a);
nwr["place"~"^(suburb|quarter|neighbourhood)$"][name](area.a);
);
out center tags;"""
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "shared/src/commonMain/composeResources/files/places.json"
BOUNDARY = DEFAULT_OUT.with_name("munich_boundary.geojson")
MERGE_METERS = 500
LANDMARK_TOURISM = ("attraction", "museum", "zoo")
DISTRICT_PLACES = ("suburb", "quarter", "neighbourhood")


def kind_of(tags):
    if "highway" in tags:
        return "street"
    if tags.get("leisure") == "park":
        return "park"
    if tags.get("place") == "square":
        return "square"
    if tags.get("railway") == "station":
        return "station"
    if tags.get("tourism") in LANDMARK_TOURISM or tags.get("leisure") == "stadium":
        return "landmark"
    if tags.get("place") in DISTRICT_PLACES:
        return "district"


def load_boundary():
    """Munich's rings: the boundary file is a world polygon with the city (and its tiny exclaves) as holes."""
    rings = json.loads(BOUNDARY.read_text())["geometry"]["coordinates"][1:]
    return [[(x, y) for x, y in ring] for ring in rings]


def in_munich(rings, lat, lon):
    """Even-odd ray casting over all rings; exclaves are separate rings, so inside any ring means inside Munich."""
    inside = False
    for ring in rings:
        for (x1, y1), (x2, y2) in zip(ring, ring[1:]):
            if (y1 > lat) != (y2 > lat) and lon < x1 + (lat - y1) * (x2 - x1) / (y2 - y1):
                inside = not inside
    return inside


def meters(a, b):
    dy = (a[0] - b[0]) * 111320
    dx = (a[1] - b[1]) * 111320 * math.cos(math.radians(a[0]))
    return math.hypot(dx, dy)


def main():
    rings = load_boundary()
    groups = defaultdict(list)
    for e in fetch(OVERPASS_URL, {"data": QUERY})["elements"]:
        tags = e.get("tags", {})
        c = e if e["type"] == "node" else e.get("center")
        kind = kind_of(tags)
        if c and kind and tags.get("name"):
            groups[(tags["name"].strip(), kind)].append((c["lat"], c["lon"]))
    places = []
    for (name, kind), pts in groups.items():
        if kind == "street":
            merged = [(sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))]
        else:
            merged = []
            for p in pts:
                if all(meters(p, q) > MERGE_METERS for q in merged):
                    merged.append(p)
        for lat, lon in merged:
            if in_munich(rings, lat, lon):
                places.append([name, kind, round(lat, 5), round(lon, 5)])
    if len(places) < 1000:
        sys.exit(f"only {len(places)} places, something is wrong")
    places.sort(key=lambda p: (p[0], p[1], p[2], p[3]))
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    out.write_text(json.dumps({"generated": generated, "places": places}, separators=(",", ":"), ensure_ascii=False), encoding="utf-8")
    counts = defaultdict(int)
    for p in places:
        counts[p[1]] += 1
    print(f"wrote {out} ({out.stat().st_size} bytes, {len(places)} places): {dict(counts)}")


main()
