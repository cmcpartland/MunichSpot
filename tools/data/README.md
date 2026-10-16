# Data pipeline

`build_pois.py` fetches the live sources and writes `tools/data/out/pois.geojson` (gitignored) with a top-level `generated` UTC timestamp. The app does not bundle it; it downloads the nightly file.

```
python3 -m venv .venv && . .venv/bin/activate
pip install -r tools/data/requirements.txt
python tools/data/build_pois.py [output-path]
```

The script exits non-zero if `validate_pois.py` finds a problem (see below). Requests that return 404, 429 or a 5xx error are retried with growing waits for up to 5 minutes; GeoServer briefly answers 404 for valid layers while it reloads.

## Nightly refresh

The `Nightly data` workflow (`.github/workflows/nightly-data.yml`) runs daily at 03:17 UTC, on manual dispatch, and on pushes to `main` that touch `tools/data/` or the workflow. It builds `pois.geojson` and publishes it to https://cmcpartland.github.io/MunichSpot/pois.geojson. If the build or the validation fails, the run fails and the last good file stays live.

One-time setup: Settings → Pages → Build and deployment → Source: GitHub Actions.

## Validation

`validate_pois.py` is called by `build_pois.py` before it writes, and runs as its own workflow step against the built file:

```
python tools/data/validate_pois.py site/pois.geojson --previous https://cmcpartland.github.io/MunichSpot/pois.geojson [--skip-previous]
```

It collects all problems and exits non-zero if there are any. Thresholds are constants at the top of the script.

- Structure: a FeatureCollection of Point features with finite coordinates, unique string ids with the prefix of their type (`awm-`, `osm-`, `tb-`, `wc-`), a known type, a string name (empty when the source has none), known materials.
- Minimums: 500 recycling islands, 500 mailboxes, 50 drinking fountains, 100 toilet locations, 1000 in total, all points inside the Munich bounding box.
- Content: at least 90 % distinct coordinates per type; recycling: 90 % with materials and at least 100 with CLOTHING; mailboxes: 80 % with `collectionTimes`; toilets: 50 % with `hours` and at most 50 % DEFECT/IMPAIRED.
- Compared with `--previous` (URL or local path): each type's count must be 80 % to 150 % of the previous count, and CLOTHING at least 80 %. Fewer than 70 % of the previous ids kept is only a warning, as is a previous file that can't be fetched or parsed (the comparison is then skipped).

It prints a previous/new/delta table and appends it to `$GITHUB_STEP_SUMMARY` when set.

If the city really removes many features, dispatch the workflow manually with `skip_previous_check` enabled. That skips only the comparison; the new file then becomes the baseline.

## City boundary

`build_boundary.py` writes `munich_boundary.geojson` (a world polygon with Munich's city boundary as a hole), which the app draws as a light veil outside the city. Run it manually with `python tools/data/build_boundary.py`; city boundaries basically never change, so it isn't part of the nightly workflow. Source: OSM relation 62428 via Overpass. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).

## Place index

`build_places.py [output-path]` writes `places.json` (compact `{"generated": <UTC timestamp>, "places": [[name, kind, lat, lon], ...]}`), the index behind the app's place search ("Wo?"): streets, parks, squares, stations, landmarks and districts in Munich. One Overpass query; streets with the same name become one entry at the mean of their ways, other features with the same name merge within 500 m. Ways and areas are returned if they touch the city, so each final point is tested against `munich_boundary.geojson` (point in polygon) and dropped if it lies outside. Without an argument it writes the bundled copy in the app (the base the app starts with); `validate_places.py <file>` checks it (parseable `generated`, at least 5000 entries, every kind present, coordinates in the Munich bounding box) and exits non-zero on problems. Places rarely change, so the nightly workflow rebuilds and validates them only weekly (Monday's scheduled run, a manual dispatch with `rebuild_places`, or when https://cmcpartland.github.io/MunichSpot/places.json isn't live yet) and publishes them next to `pois.geojson`. Any other run, and any places failure, re-publishes the live file (or the bundled one on the first deploy) because every deploy replaces the whole site; a places problem never fails the job. The app only takes a downloaded file whose `generated` is newer than the one it has. Source: OSM via Overpass. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).

## Sources

- Recycling islands: Landeshauptstadt München, AWM, WFS `gsm_wfs:awm_container` (EPSG:25832, reprojected to WGS84). [dl-de/by-2.0](https://www.govdata.de/dl-de/by-2-0).
- Mailboxes: OpenStreetMap `amenity=post_box` in Munich via the Overpass API. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
- Drinking fountains: Landeshauptstadt München, WFS `baug_wfs:trinkwasserbrunnen`, dataset `opendata_trinkwasserbrunnen` (EPSG:25832, reprojected to WGS84). [dl-de/by-2.0](https://www.govdata.de/dl-de/by-2-0).
- Public toilets: Landeshauptstadt München, WFS `gsm_wfs:wc_finder_opendata`, dataset `wc_finder` (EPSG:25832, reprojected to WGS84). The dataset has one feature per room; rooms within 15 m are merged into one location. [dl-de/by-2.0](https://www.govdata.de/dl-de/by-2-0).
