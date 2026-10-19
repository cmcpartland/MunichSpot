# Observability of the nightly data pipeline

How to tell whether the `Nightly data` workflow (`.github/workflows/nightly-data.yml`) worked, what went wrong if it didn't, and how the data has changed over time. For what the pipeline does, see `README.md` in this folder.

## Overview

| Question | Where to look |
|---|---|
| Did tonight's run work? | Healthchecks.io check (alerts you), or Actions → Nightly data |
| Did it work but with problems? | Annotations on the run page |
| What did it produce? | The run's job summary |
| Why did a step fail? | The run's logs |
| How has the data changed over weeks or months? | `history.jsonl` on the `data-history` branch |
| What's live right now? | https://cmcpartland.github.io/MunichSpot/ (each file has a `generated` timestamp) |

## Failure handling

A run fails only if `build_pois.py` or `validate_pois.py` fails. The deploy job is then skipped, and the last good `pois.geojson` stays live. Users see no change except that the data gets older; past 30 days the app shows "Datenstand: …" in the attribution bar.

These never fail a run; they only raise warnings:
- DeepL or translation-cache problems (texts without a translation show in German).
- places.json build or validation problems (the live file, or the bundled copy, is re-published).
- Mailbox fetches from Overpass, which often return 504, are retried for up to 5 minutes inside the build. If they still fail, the build fails.

## Alerts: Healthchecks.io

A Healthchecks.io check ("MunichSpot nightly data", period 1 day, grace 12 h) works as a dead man's switch. The `healthcheck` job runs at the end of every run on `main`:
- deploy succeeded: it pings the check (up);
- build or deploy failed or was skipped: it pings `/fail`, which alerts right away.

If no successful ping arrives for about 36 hours, Healthchecks.io alerts too. This catches what GitHub can't report itself: a schedule that stopped firing. GitHub disables scheduled workflows after 60 days without repository activity; re-enable it under Actions → Nightly data → "Enable workflow".

Each ping's body is `deploy: <result>, <run URL>`, so the check's event log links straight to the run. The ping URL is the repository secret `HEALTHCHECK_URL`; without it the job prints "HEALTHCHECK_URL not set, skipping". Pings come only from runs on `main`, so test dispatches on feature branches don't affect the check. Alert channels (email by default) are set under Integrations in the Healthchecks.io project.

The job has `continue-on-error`, so a Healthchecks.io outage never turns a run red.

## Annotations

The scripts report problems through `ci.warn()` (`tools/data/ci.py`). In Actions this prints `::warning::…`, which GitHub shows as an annotation on the run page and in the run list, even when the run passed. Locally it prints `WARNING: …`.

Current warnings include:
- `build_pois.py`: unknown toilet `zustand` values (a new condition code the script doesn't map).
- `translate.py`: no translation cache, DeepL failed, `DEEPL_AUTH_KEY` missing, overrides whose German text no longer appears.
- `validate_pois.py`: previous file unusable (comparison skipped), fewer than 70 % of previous ids kept, fewer than 90 % of toilet hours translated.
- `validate_places.py` and the `Places summary` step: places validation errors, a failed rebuild, or a fallback to the bundled copy.

Validation errors (as opposed to warnings) fail the run and appear in the failed step's log.

## Job summary

On the run page, below the job graph:
- **pois.geojson: previous vs new**: count per type, compared with the live file, with deltas.
- **Translations**: distinct texts, how many came from overrides, the cache and DeepL, how many are untranslated, and any unused overrides.
- **places.json**: rebuilt (with count), not due and re-published, rebuild failed, or bundled copy published.
- **Record**: confirmation that the run was written to `history.jsonl`.

GitHub keeps run logs and summaries for 90 days, and there's no API for summaries, so use the run history for anything older or for comparisons.

## Run history

Every run, failed or not, appends one JSON line to `history.jsonl` on the `data-history` branch (the `record` job; a short-lived `run-record` artifact passes the data from the build job). See "Run history" in `README.md` for the schema. In short, each line has:
- `run`: id, number, event, commit, time, URL;
- `result` and `steps`: build/deploy results and the outcome of each build step;
- `pois`: counts per type, material counts, toilet field counts, translation counts (null if the build failed);
- `places`: result, count, `generated`;
- `warnings`: the run's warnings (up to 50).

Reading it:

```
git fetch origin data-history
git show origin/data-history:history.jsonl | jq -c '{n: .run.number, time: .run.recorded, counts: .pois.counts}'
```

or https://raw.githubusercontent.com/cmcpartland/MunichSpot/data-history/history.jsonl.

Useful queries:

```
# runs that failed or had warnings
... | jq -c 'select(.result.deploy != "success" or (.warnings | length) > 0) | {n: .run.number, result, warnings}'
# toilet coverage over time
... | jq -c '{t: .run.recorded, toilets: .pois.counts.TOILET, hours: .pois.toilet.hours}'
```

The `record` job only needs `contents: write` itself, and it has `continue-on-error`, so a failed push never turns a run red. The first line (run 22) is a test from a feature branch whose deploy was refused by the Pages environment rules.

## Running a check by hand

- Rerun now: `gh workflow run nightly-data.yml` (add `-f rebuild_places=true` to rebuild places, `-f skip_previous_check=true` to accept a real large change).
- Latest runs: `gh run list --workflow nightly-data.yml`.
- Warnings in a run: `gh run view <id> --log | grep -E '::warning::|WARNING'`.
- Locally: `python tools/data/build_pois.py` prints the same warnings and counts.

## Not covered

- The app itself: there's no crash reporting or usage analytics. Adding them would change the App Store privacy label and the Play data-safety form.
- Data correctness beyond the validation thresholds: a source can be wrong while still passing every check.
