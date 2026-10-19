#!/usr/bin/env python3
"""Fetches Munich recycling islands (AWM), mailboxes (OSM) drinking fountains and public toilets and writes pois.geojson."""
import itertools
import json
import math
import os
import re
import sys
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import requests
from pyproj import Transformer
from ci import warn
from translate import translate_features
from validate_pois import validate

AWM_URL = (
    "https://geoportal.muenchen.de/geoserver/gsm_wfs/ows?service=WFS&version=1.0.0"
    "&request=GetFeature&typeName=gsm_wfs:awm_container&outputFormat=application/json"
)
FOUNTAIN_URL = (
    "https://geoportal.muenchen.de/geoserver/baug_wfs/ows?service=WFS&version=1.0.0"
    "&request=GetFeature&typeName=baug_wfs:trinkwasserbrunnen&outputFormat=application/json"
)
TOILET_URL = (
    "https://geoportal.muenchen.de/geoserver/gsm_wfs/ows?service=WFS&version=1.1.0"
    "&request=GetFeature&typeName=gsm_wfs:wc_finder_opendata&outputFormat=application/json"
)
OVERPASS_URL = "https://overpass-api.de/api/interpreter"
OVERPASS_QUERY = """[out:json][timeout:120];
area["boundary"="administrative"]["admin_level"="6"]["name"="München"]["de:amtlicher_gemeindeschluessel"="09162000"]->.a;
nwr["amenity"="post_box"](area.a);
out center tags;"""
USER_AGENT = "MunichSpot-data/0.1 (+https://github.com/cmcpartland/MunichSpot)"
DEFAULT_OUT = Path(__file__).resolve().parent / "out/pois.geojson"

TOILET_MERGE_METERS = 15
ROOM_CODES = (("männlich", "M"), ("weiblich", "F"), ("unisex", "U"), ("barrierefrei", "A"))
RETRY_STATUSES = (404, 429, 502, 503, 504)
RETRY_SECONDS = 300


def fetch(url, data=None):
    """Retries 404 (GeoServer briefly 404s valid layers while reloading), rate limits and 5xx for up to RETRY_SECONDS."""
    start = time.monotonic()
    for attempt in itertools.count(1):
        resp = requests.request(
            "POST" if data else "GET", url, data=data, headers={"User-Agent": USER_AGENT}, timeout=180
        )
        if resp.ok:
            return resp.json()
        wait = min(10 * attempt, 60)
        if resp.status_code not in RETRY_STATUSES or time.monotonic() - start + wait > RETRY_SECONDS:
            resp.raise_for_status()
        print(f"{resp.status_code} from {url.split('?')[0]}, retry {attempt} in {wait} s")
        time.sleep(wait)


MATERIAL_COLUMNS = {
    "glas_braun": "GLASS_BROWN",
    "glas_gruen": "GLASS_GREEN",
    "glas_weiss": "GLASS_WHITE",
    "lvp": "PACKAGING",
    "altkleider": "CLOTHING",
}


def feature(id_, type_, name, lon, lat, materials=None, note=None, **extra):
    f = {
        "type": "Feature",
        "id": id_,
        "geometry": {"type": "Point", "coordinates": [round(lon, 5), round(lat, 5)]},
        "properties": {"type": type_, "name": name},
    }
    if materials is not None:
        f["properties"]["materials"] = materials
    if note:
        f["properties"]["note"] = note
    f["properties"].update(extra)
    return f


to_wgs84 = Transformer.from_crs("EPSG:25832", "EPSG:4326", always_xy=True)


def recycling_features():
    for f in fetch(AWM_URL)["features"]:
        x, y = f["geometry"]["coordinates"][:2]
        lon, lat = to_wgs84.transform(x, y)
        name = (f["properties"].get("adresse") or "").strip()
        materials = [m for col, m in MATERIAL_COLUMNS.items() if (f["properties"].get(col) or 0) > 0]
        yield feature("awm-" + f["id"], "RECYCLING", name, lon, lat, materials)


def mailbox_features():
    for e in fetch(OVERPASS_URL, {"data": OVERPASS_QUERY})["elements"]:
        point = e if "lat" in e else e.get("center")
        if point is None:
            continue
        tags = e.get("tags", {})
        extra = {}
        if times := (tags.get("collection_times") or "").strip():
            extra["collectionTimes"] = times
        if address := (tags.get("ref") or "").strip():
            extra["address"] = address
        if tags.get("indoor") == "yes":
            extra["indoor"] = True
        name = (tags.get("name") or "").strip()
        yield feature(f"osm-{e['id']}", "POST_BOX", name, point["lon"], point["lat"], **extra)


GENERIC_FOUNTAIN_TEXT = {"trinkbrunnen", "trinkwasserbrunnen", "trinkwasserspender", "kein name"}


def fountain_features():
    for f in fetch(FOUNTAIN_URL)["features"]:
        x, y = f["geometry"]["coordinates"][:2]
        lon, lat = to_wgs84.transform(x, y)
        name = " ".join((f["properties"].get("bezeichnung") or "").split())
        if name.lower() in GENERIC_FOUNTAIN_TEXT:
            name = ""
        note = (f["properties"].get("bemerkung") or "").strip()
        if note.lower() in GENERIC_FOUNTAIN_TEXT:
            note = ""
        yield feature("tb-" + f["id"], "DRINKING_WATER", name, lon, lat, note=note)


def clean(value):
    return " ".join(str(value or "").split())


def toilet_groups(rooms):
    parent = list(range(len(rooms)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i, a in enumerate(rooms):
        for j in range(i):
            if math.dist(a["geometry"]["coordinates"][:2], rooms[j]["geometry"]["coordinates"][:2]) <= TOILET_MERGE_METERS:
                parent[find(i)] = find(j)
    groups = {}
    for i, room in enumerate(rooms):
        groups.setdefault(find(i), []).append(room)
    return groups.values()


# Names that describe the operator or the room, not the place.
NOT_TOILET_NAMES = {"DSMDecaux GmbH", "Behindertengerecht", "Behinderten"}


def toilet_name(raw):
    """Turns a room name into a place name: drops the room code (B/D/H/M) and the generic prefix."""
    name = re.sub(r"\s[BDHM]$", "", clean(raw))
    if name.startswith("Öffentliche Toilette "):
        name = name[len("Öffentliche Toilette "):].strip()
    if name[:1] in "\"„“":
        name = clean(re.sub(r"[\"„“”]", " ", name))
    return "" if name in NOT_TOILET_NAMES else name


def toilet_features():
    rooms = fetch(TOILET_URL)["features"]
    print(f"toilet rooms read: {len(rooms)}")
    groups = list(toilet_groups(rooms))
    known = {"voll funktionsfähig", "defekt oder nicht zugänglich", "teilweise Beeinträchtigung"}
    unknown = Counter(r["properties"].get("zustand") for r in rooms if r["properties"].get("zustand") not in known)
    if unknown:
        warn(f"unknown toilet zustand values: {dict(unknown)}")
    for group in groups:
        props = [r["properties"] for r in group]
        x = sum(r["geometry"]["coordinates"][0] for r in group) / len(group)
        y = sum(r["geometry"]["coordinates"][1] for r in group) / len(group)
        lon, lat = to_wgs84.transform(x, y)
        names = Counter(n for n in (toilet_name(p.get("name")) for p in props) if n)
        name = min(names, key=lambda n: (-names[n], n)) if names else ""
        first = min(group, key=lambda r: int(r["id"].rsplit(".", 1)[1]))
        categories = [p.get("kategorie") or "" for p in props]

        def distinct(key):
            return list(dict.fromkeys(v for v in (clean(p.get(key)) for p in props) if v))

        extra = {}
        for p in props:
            street = clean(f"{clean(p.get('strasse'))} {clean(p.get('hausnr'))}")
            if street:
                plz = clean(p.get("plz"))
                extra["address"] = f"{street}, {plz}" if plz else street
                break
        if hours := distinct("oeffnungszeiten"):
            extra["hours"] = " / ".join(hours)
        if floors := distinct("etage"):
            extra["floor"] = " / ".join(floors)
        if prices := [p["preis"] for p in props if isinstance(p.get("preis"), (int, float))]:
            extra["price"] = min(prices)
        if any("barrierefrei" in c for c in categories):
            extra["accessible"] = True
        if any(p.get("wickeln") == 1 for p in props):
            extra["changing"] = True
        if any(p.get("wctuer_euro_schluessel") == 1 or p.get("eingangstuer_euro_schluessel") == 1 for p in props):
            extra["euroKey"] = True
        if codes := sorted({code for c in categories for word, code in ROOM_CODES if word in c}):
            extra["rooms"] = codes
        states = {p.get("zustand") for p in props}
        if "defekt oder nicht zugänglich" in states:
            extra["status"] = "DEFECT"
        elif "teilweise Beeinträchtigung" in states:
            extra["status"] = "IMPAIRED"
        yield feature("wc-" + first["id"], "TOILET", name, lon, lat, **extra)


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    features = sorted([*recycling_features(), *mailbox_features(), *fountain_features(), *toilet_features()], key=lambda f: f["id"])

    translations = translate_features([f for f in features if f["properties"]["type"] in ("TOILET", "DRINKING_WATER")], out)

    errors, warnings = validate(features)
    for w in warnings:
        print(f"WARNING: {w}")
    if errors:
        sys.exit("sanity check failed:\n" + "\n".join(errors[:50]))
    counts = Counter(f["properties"]["type"] for f in features)
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        json.dumps(
            {"type": "FeatureCollection", "generated": generated, "features": features},
            separators=(",", ":"),
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    print(", ".join(f"{t}: {n}" for t, n in counts.items()))
    materials = {m: sum(m in f["properties"].get("materials", ()) for f in features) for m in MATERIAL_COLUMNS.values()}
    materials["none"] = sum(f["properties"].get("materials") == [] for f in features)
    for m, n in materials.items():
        print(f"  {m}: {n}")
    print(f"toilet locations: {counts['TOILET']}")
    toilet = {
        key: sum(key in f["properties"] for f in features if f["properties"]["type"] == "TOILET")
        for key in ("address", "hours", "floor", "price", "accessible", "changing", "euroKey", "rooms", "status")
    }
    for key, n in toilet.items():
        print(f"  {key}: {n}")
    size = out.stat().st_size
    print(f"wrote {out} ({size} bytes)")
    if record_dir := os.environ.get("RUN_RECORD_DIR"):
        Path(record_dir).mkdir(parents=True, exist_ok=True)
        (Path(record_dir) / "pois.json").write_text(
            json.dumps(
                {
                    "generated": generated,
                    "bytes": size,
                    "counts": dict(counts),
                    "materials": materials,
                    "toilet": toilet,
                    "translations": translations,
                },
                separators=(",", ":"),
            ),
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
