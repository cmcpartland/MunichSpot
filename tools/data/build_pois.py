#!/usr/bin/env python3
"""Fetches Munich recycling islands (AWM), mailboxes (OSM) and drinking fountains and writes pois.geojson."""
import json
import sys
import time
from pathlib import Path

import requests
from pyproj import Transformer

AWM_URL = (
    "https://geoportal.muenchen.de/geoserver/gsm_wfs/ows?service=WFS&version=1.0.0"
    "&request=GetFeature&typeName=gsm_wfs:awm_container&outputFormat=application/json"
)
FOUNTAIN_URL = (
    "https://geoportal.muenchen.de/geoserver/baug_wfs/ows?service=WFS&version=1.0.0"
    "&request=GetFeature&typeName=baug_wfs:trinkwasserbrunnen&outputFormat=application/json"
)
OVERPASS_URL = "https://overpass-api.de/api/interpreter"
OVERPASS_QUERY = """[out:json][timeout:120];
area["boundary"="administrative"]["admin_level"="6"]["name"="München"]["de:amtlicher_gemeindeschluessel"="09162000"]->.a;
nwr["amenity"="post_box"](area.a);
out center tags;"""
USER_AGENT = "MunichSpot-data/0.1 (+https://github.com/cmcpartland/MunichSpot)"
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "shared/src/commonMain/composeResources/files/pois.geojson"

MIN_PER_TYPE = {"RECYCLING": 500, "POST_BOX": 500, "DRINKING_WATER": 50}
LAT_RANGE = (47.9, 48.4)
LON_RANGE = (11.2, 11.9)


def fetch(url, data=None, attempts=4):
    for attempt in range(1, attempts + 1):
        resp = requests.request(
            "POST" if data else "GET", url, data=data, headers={"User-Agent": USER_AGENT}, timeout=180
        )
        if resp.ok:
            return resp.json()
        if attempt == attempts or resp.status_code not in (429, 502, 503, 504):
            resp.raise_for_status()
        time.sleep(10 * attempt)


MATERIAL_COLUMNS = {
    "glas_braun": "GLASS_BROWN",
    "glas_gruen": "GLASS_GREEN",
    "glas_weiss": "GLASS_WHITE",
    "lvp": "PACKAGING",
    "altkleider": "CLOTHING",
}


def feature(id_, type_, name, lon, lat, materials=None, note=None):
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
    return f


to_wgs84 = Transformer.from_crs("EPSG:25832", "EPSG:4326", always_xy=True)


def recycling_features():
    for f in fetch(AWM_URL)["features"]:
        x, y = f["geometry"]["coordinates"][:2]
        lon, lat = to_wgs84.transform(x, y)
        name = (f["properties"].get("adresse") or "").strip() or "Wertstoffinsel"
        materials = [m for col, m in MATERIAL_COLUMNS.items() if (f["properties"].get(col) or 0) > 0]
        yield feature("awm-" + f["id"], "RECYCLING", name, lon, lat, materials)


def mailbox_features():
    for e in fetch(OVERPASS_URL, {"data": OVERPASS_QUERY})["elements"]:
        point = e if "lat" in e else e.get("center")
        if point is None:
            continue
        times = (e.get("tags", {}).get("collection_times") or "").strip()
        name = f"Briefkasten ({times})" if times else "Briefkasten"
        yield feature(f"osm-{e['id']}", "POST_BOX", name, point["lon"], point["lat"])


def fountain_features():
    for f in fetch(FOUNTAIN_URL)["features"]:
        x, y = f["geometry"]["coordinates"][:2]
        lon, lat = to_wgs84.transform(x, y)
        name = " ".join((f["properties"].get("bezeichnung") or "").split()) or "Trinkbrunnen"
        note = (f["properties"].get("bemerkung") or "").strip()
        yield feature("tb-" + f["id"], "DRINKING_WATER", name, lon, lat, note=note)


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUT
    features = sorted([*recycling_features(), *mailbox_features(), *fountain_features()], key=lambda f: f["id"])

    counts = {t: sum(f["properties"]["type"] == t for f in features) for t in MIN_PER_TYPE}
    errors = [f"only {n} {t} features" for t, n in counts.items() if n < MIN_PER_TYPE[t]]
    for f in features:
        lon, lat = f["geometry"]["coordinates"]
        if not (LAT_RANGE[0] <= lat <= LAT_RANGE[1] and LON_RANGE[0] <= lon <= LON_RANGE[1]):
            errors.append(f"{f['id']} outside Munich: {lat}, {lon}")
    if errors:
        sys.exit("sanity check failed:\n" + "\n".join(errors))

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        json.dumps({"type": "FeatureCollection", "features": features}, separators=(",", ":"), ensure_ascii=False),
        encoding="utf-8",
    )
    print(", ".join(f"{t}: {n}" for t, n in counts.items()))
    for m in MATERIAL_COLUMNS.values():
        print(f"  {m}: {sum(m in f['properties'].get('materials', ()) for f in features)}")
    print(f"  none: {sum(f['properties'].get('materials') == [] for f in features)}")
    print(f"wrote {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
