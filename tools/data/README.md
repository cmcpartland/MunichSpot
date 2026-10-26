# Data pipeline

`build_pois.py` fetches the live sources and writes `tools/data/out/pois.geojson` (gitignored) with a top-level `schemaVersion` (integer, `SCHEMA_VERSION` in `validate_pois.py`) and `generated` UTC timestamp. The app does not bundle it; it downloads the nightly file.

```
python3 -m venv .venv && . .venv/bin/activate
pip install -r tools/data/requirements.txt
python tools/data/build_pois.py [output-path]
```

The script exits non-zero if `validate_pois.py` finds a problem (see below). Requests that return 404, 429 or a 5xx error are retried with growing waits for up to 5 minutes; GeoServer briefly answers 404 for valid layers while it reloads.

## Nightly refresh

The `Nightly data` workflow (`.github/workflows/nightly-data.yml`) runs daily at 03:17 UTC, on manual dispatch, and on pushes to `main` that touch `tools/data/` or the workflow. It builds `pois.geojson` and publishes it to https://cmcpartland.github.io/MunichSpot/pois.geojson. If the build or the validation fails, the run fails and the last good file stays live.

One-time setup: Settings → Pages → Build and deployment → Source: GitHub Actions.

In the nightly workflow, warnings from these scripts (`ci.warn`) show up as annotations on the run (plain `WARNING:` lines locally). The job summary also has a `### places.json` section saying whether places.json was rebuilt, re-published from the live file or replaced by the bundled copy; the last two after a failure also raise an annotation.

Alerts: a Healthchecks.io check (period 1 day, grace 12 h) is pinged by the `healthcheck` job after every run on `main`; its URL is the `HEALTHCHECK_URL` repo secret, and without the secret the job is skipped. A failed run pings `/fail` and alerts immediately; no successful deploy for ~36 h (e.g. the run never started) alerts by email. If GitHub disabled the schedule after 60 days without repo activity, re-enable it under Actions → Nightly data.

How to see what a run did and what went wrong (alerts, annotations, job summary, run history): [OBSERVABILITY.md](OBSERVABILITY.md).

## Validation

`validate_pois.py` is called by `build_pois.py` before it writes, and runs as its own workflow step against the built file:

```
python tools/data/validate_pois.py site/pois.geojson --previous https://cmcpartland.github.io/MunichSpot/pois.geojson [--skip-previous]
```

It collects all problems and exits non-zero if there are any. Thresholds are constants at the top of the script.

- Structure: a FeatureCollection of Point features with finite coordinates, unique string ids with the prefix of their type (`awm-`, `osm-`, `tb-`, `wc-`, `wsh-`), a known type, a string name (empty when the source has none), known materials.
- Top level: `schemaVersion` equals the version the builder writes; `generated` is an ISO 8601 timestamp.
- Minimums: 500 recycling islands, 500 mailboxes, 50 drinking fountains, 100 toilet locations, 10 recycling centres, 50 public bookcases, 1000 in total, all points inside the Munich bounding box.
- Content: at least 90 % distinct coordinates per type; recycling: 90 % with materials and at least 100 with CLOTHING; mailboxes: 80 % with `collectionTimes`; toilets: 50 % with `hours` and at most 50 % DEFECT/IMPAIRED; recycling centres: a warning if fewer than 80 % have a `schedule`.
- Compared with `--previous` (URL or local path): each type's count must be 80 % to 150 % of the previous count, and CLOTHING at least 80 %. Fewer than 70 % of the previous ids kept is only a warning, as is a previous file that can't be fetched or parsed (the comparison is then skipped).

It prints a previous/new/delta table and appends it to `$GITHUB_STEP_SUMMARY` when set.

If the city really removes many features, dispatch the workflow manually with `skip_previous_check` enabled. That skips only the comparison; the new file then becomes the baseline.

## Data contract

The app accepts a `pois.geojson` whose `schemaVersion` is missing or at most the version it knows (`POI_SCHEMA_VERSION` in `PoiRepository.kt`), and rejects a newer or non-integer one, keeping its cache. Additive changes (new optional properties, new types the app ignores) keep the version. A breaking change bumps the version and publishes under a new file name (e.g. `pois.v2.geojson`), while the old file keeps being published in the old format until old app versions are gone; the app's `LATEST_POIS_URL` then moves to the new name.

## Translations

`translate.py` (called by `build_pois.py`) adds `hoursEn` (toilet opening hours, translated per segment between " / " and " | ") and `noteEn` (fountain and bookcase notes) to the features; the app shows them on English devices with an "Automatically translated" label; dotted times in `hoursEn` ("11.00 am") are rewritten to colons ("11:00 am"). For each distinct German text it looks in `translation_overrides.json` (committed `{german: english}`, wins over everything), then in the cache, then asks DeepL (EN-GB) for the rest. Overrides match the exact German text too, so an override whose text no longer appears (the city edited or removed it) is reported as a warning and in the job summary; update or delete its key. The cache is the live https://cmcpartland.github.io/MunichSpot/translations.json (`{"generated", "entries": {german: english}}`, keyed by the exact whitespace-normalised German text), so only new or changed text goes to DeepL. For local runs, `TRANSLATIONS_CACHE` can point to another URL or file. The file is written next to the pois output and pruned to the texts currently in use; counts are printed and added to the job summary.

The DeepL key is the repository secret `DEEPL_AUTH_KEY` (a key ending in `:fx` uses the free API). Without a key, or if DeepL fails, the build continues with cache and overrides only; texts without a translation simply have no `…En` field and show in German. `validate_pois.py` warns if fewer than 90 % of toilets with hours have `hoursEn`.

## Toilet opening hours (`schedule`)

`hours.py` (`parse_schedule`) turns the free-text `hours` of a toilet into the structured property `schedule`, a list of rules `{"from": min, "to": min}` with optional `"days"` (ISO weekdays 1 = Monday .. 7, absent = every day) and optional `"months"` (1..12, absent = all year). Minutes count from local midnight; `to` may exceed 1440 for hours past midnight (06:00-02:00 is 360..1560), 24:00 is 1440. The toilet is open at a local time if any rule matches (a rule spills into the next day's early hours with the days/months of its own day) and closed otherwise, so a schedule is only written when it describes all opening times. The parser is a strict whitelist: daily ranges, always open ("Durchgehend", "24 Stunden", flood-only notes), weekday ranges and lists (Mo-Sa, wochentags, samstags, "So/Feiertag" counts as Sunday; holidays aren't modelled), month-dependent hours and "ganztägig, Mai bis Oktober". Everything else (cemetery, kiosk or café dependent, events, months without times) gives no `schedule` and the app treats it as unknown. Texts merged with " / " are parsed per part; the schedule is the union, or none if any part fails. `build_pois.py` prints the hours strings without a schedule ("hours without schedule: ...") so new formats show up in the logs, and warns if fewer than half of the toilets with hours get one. `validate_pois.py` checks the shape. Test with `python3 tools/data/test_hours.py` (also run by the nightly workflow before the build).

## Recycling centre opening hours

Recycling centres (Wertstoffhöfe, type `RECYCLING_CENTRE`, ids `wsh-<wfs id>`) take their location and `address` from the city WFS layer. Its names are useless ("WSH"), so `build_pois.py` matches each site to the nearest AWM recycling centre in OSM within 150 m (distance in EPSG:25832 metres; AWM = operator contains "Abfallwirtschaftsbetrieb" or name starts with "Wertstoffhof") and takes `name` and `opening_hours` from it. The Overpass query returns mailboxes and recycling centres in one request. `osm_hours.py` (`parse_opening_hours`) turns the OSM text into `schedule` (same rule format as above, no months) with a strict whitelist: `24/7` or rules joined by `;`, each `<days> <HH:MM-HH:MM[,HH:MM-HH:MM]>` with days as `Mo`, `Mo-Fr` or `Mo,We,Fr`. Anything else (`PH`, `off`, comments, months, `+`, sunrise) or a day in more than one rule gives no `schedule` and the app treats the centre as unknown. Sites without an OSM match or schedule produce a warning. Test with `python3 tools/data/test_osm_hours.py` (also run by the nightly workflow).

## Public bookcases

Public bookcases (Bücherschränke, type `PUBLIC_BOOKCASE`, ids `osm-<n|w|r><osm id>`, so they never clash with mailbox ids) come from the same Overpass request (`amenity=public_bookcase`). Those with `access` private, no or customers are skipped. `name` is the OSM name (often empty), `note` the OSM `description` (translated to `noteEn`), `phoneBox` is set for `public_bookcase:type=phone_box`, `indoor` for `indoor=yes` or `public_bookcase:type=building`, and `schedule` comes from `opening_hours` via `osm_hours.py` when it parses (the app treats the others as always accessible).

## City boundary

`build_boundary.py` writes `munich_boundary.geojson` (a world polygon with Munich's city boundary as a hole), which the app draws as a light veil outside the city. Run it manually with `python tools/data/build_boundary.py`; city boundaries basically never change, so it isn't part of the nightly workflow. Source: OSM relation 62428 via Overpass. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).

## Place index

`build_places.py [output-path]` writes `places.json` (compact `{"generated": <UTC timestamp>, "places": [[name, kind, lat, lon], ...]}`), the index behind the app's place search ("Wo?"): streets, parks, squares, stations, landmarks and districts in Munich. One Overpass query; streets with the same name become one entry at the mean of their ways, other features with the same name merge within 500 m. Ways and areas are returned if they touch the city, so each final point is tested against `munich_boundary.geojson` (point in polygon) and dropped if it lies outside. Without an argument it writes the bundled copy in the app (the base the app starts with); `validate_places.py <file>` checks it (parseable `generated`, at least 5000 entries, every kind present, coordinates in the Munich bounding box) and exits non-zero on problems. Places rarely change, so the nightly workflow rebuilds and validates them only weekly (Monday's scheduled run, a manual dispatch with `rebuild_places`, or when https://cmcpartland.github.io/MunichSpot/places.json isn't live yet) and publishes them next to `pois.geojson`. Any other run, and any places failure, re-publishes the live file (or the bundled one on the first deploy) because every deploy replaces the whole site; a places problem never fails the job. The app only takes a downloaded file whose `generated` is newer than the one it has. Source: OSM via Overpass. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).

## Run history

Every run of the nightly workflow, failed ones included, appends one compact JSON line to `history.jsonl` on the orphan branch `data-history` (created by the first run; the `record` job needs `contents: write` and never turns the workflow red). Keys, in order: `run` (`id`, `number`, `attempt`, `event`, `sha`, `recorded` UTC, `url`), `result` (`build`, `deploy` job results), `steps` (outcomes of `pois`, `validate`, `places`), `pois` (`generated`, `bytes`, `counts` per type, `materials` counts with `none`, `toilet` field counts, `translations` stats), `places` (`result`: `rebuilt`, `not_due`, `failed` or `bundled`, plus `count` and `generated`) and `warnings` (at most 50 lines, 300 characters each). Parts that were never produced, such as `pois` after a failed build, are `null`.

The build job writes its inputs to `$RUN_RECORD_DIR` (`pois.json`, `places.json`, `warnings.log`, uploaded as the `run-record` artifact); `record_run.py <record-dir> <history-file>` assembles the line. Locally `RUN_RECORD_DIR` is unset and nothing is written.

```sh
git fetch origin data-history && git show origin/data-history:history.jsonl | jq -c '{n: .run.number, counts: .pois.counts}'
```

Raw file: https://raw.githubusercontent.com/cmcpartland/MunichSpot/data-history/history.jsonl

## Sources

- Recycling islands: Landeshauptstadt München, AWM, WFS `gsm_wfs:awm_container` (EPSG:25832, reprojected to WGS84). [dl-de/by-2.0](https://www.govdata.de/dl-de/by-2-0).
- Recycling centres: Landeshauptstadt München, AWM, WFS `gsm_wfs:awm_wertstoffhoefe` (EPSG:25832, reprojected to WGS84; locations and addresses). [dl-de/by-2.0](https://www.govdata.de/dl-de/by-2-0). Names and opening hours: OpenStreetMap `amenity=recycling` + `recycling_type=centre` via Overpass. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
- Mailboxes: OpenStreetMap `amenity=post_box` in Munich via the Overpass API. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
- Public bookcases: OpenStreetMap `amenity=public_bookcase` in Munich via the Overpass API. © OpenStreetMap contributors, [ODbL 1.0](https://www.openstreetmap.org/copyright).
- Drinking fountains: Landeshauptstadt München, WFS `baug_wfs:trinkwasserbrunnen`, dataset `opendata_trinkwasserbrunnen` (EPSG:25832, reprojected to WGS84). [dl-de/by-2.0](https://www.govdata.de/dl-de/by-2-0).
- Public toilets: Landeshauptstadt München, WFS `gsm_wfs:wc_finder_opendata`, dataset `wc_finder` (EPSG:25832, reprojected to WGS84). The dataset has one feature per room; rooms within 15 m are merged into one location. Opening times are normalised from dot format (e.g. "06.00") to HH:MM (e.g. "06:00"). [dl-de/by-2.0](https://www.govdata.de/dl-de/by-2-0).
