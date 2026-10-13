# Data pipeline

`build_pois.py` fetches the live sources and writes `shared/src/commonMain/composeResources/files/pois.geojson`, which the app bundles.

```
python3 -m venv .venv && . .venv/bin/activate
pip install -r tools/data/requirements.txt
python tools/data/build_pois.py [output-path]
```

The script exits non-zero if either type has fewer than 500 features or any point lies outside the Munich area.

## Sources

- Recycling islands: Landeshauptstadt München, AWM, WFS `gsm_wfs:awm_container` (EPSG:25832, reprojected to WGS84). [dl-de/by-2.0](https://www.govdata.de/dl-de/by-2-0).
- Mailboxes: OpenStreetMap `amenity=post_box` in Munich via the Overpass API. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
