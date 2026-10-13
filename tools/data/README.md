# Data pipeline

`build_pois.py` fetches the live sources and writes `shared/src/commonMain/composeResources/files/pois.geojson`, which the app bundles.

```
python3 -m venv .venv && . .venv/bin/activate
pip install -r tools/data/requirements.txt
python tools/data/build_pois.py [output-path]
```

The script exits non-zero if either type has fewer than 500 features or any point lies outside the Munich area.

## Nightly refresh

The `Nightly data` workflow (`.github/workflows/nightly-data.yml`) runs daily at 03:17 UTC, on manual dispatch, and on pushes to `main` that touch `tools/data/` or the workflow. It builds `pois.geojson` and publishes it to https://cmcpartland.github.io/MunichSpot/pois.geojson. If the script fails its sanity checks, the run fails and the last good file stays live.

One-time setup: Settings → Pages → Build and deployment → Source: GitHub Actions.

## Sources

- Recycling islands: Landeshauptstadt München, AWM, WFS `gsm_wfs:awm_container` (EPSG:25832, reprojected to WGS84). [dl-de/by-2.0](https://www.govdata.de/dl-de/by-2-0).
- Mailboxes: OpenStreetMap `amenity=post_box` in Munich via the Overpass API. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
