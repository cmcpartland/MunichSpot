"""Adds English translations (hoursEn, noteEn) of German free text, via overrides, the live cache and DeepL."""
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path

import requests

OVERRIDES_PATH = Path(__file__).resolve().parent / "translation_overrides.json"
LIVE_TRANSLATIONS_URL = "https://cmcpartland.github.io/MunichSpot/translations.json"
DEEPL_CONTEXT = "Opening hours and notes of public toilets and drinking fountains in Munich, Germany."
DEEPL_BATCH = 50
SEPARATORS = re.compile(r"( / | \| )")


def norm(text):
    return " ".join(text.split())


def load_entries(source):
    """Reads {de: en} from a URL or path; any problem gives an empty dict and a warning."""
    try:
        if source.startswith("http"):
            resp = requests.get(source, timeout=60)
            resp.raise_for_status()
            data = resp.json()
        else:
            data = json.loads(Path(source).read_text(encoding="utf-8"))
        entries = data["entries"]
        return {k: v for k, v in entries.items() if isinstance(k, str) and isinstance(v, str) and v.strip()}
    except Exception as e:
        print(f"WARNING: no translation cache from {source}: {e}")
        return {}


def deepl_translate(texts, key):
    """Returns {de: en} for what DeepL translated; stops at the first error."""
    base = "https://api-free.deepl.com" if key.endswith(":fx") else "https://api.deepl.com"
    result = {}
    try:
        for i in range(0, len(texts), DEEPL_BATCH):
            chunk = texts[i : i + DEEPL_BATCH]
            resp = requests.post(
                f"{base}/v2/translate",
                headers={"Authorization": f"DeepL-Auth-Key {key}"},
                json={"text": chunk, "source_lang": "DE", "target_lang": "EN-GB", "context": DEEPL_CONTEXT},
                timeout=60,
            )
            resp.raise_for_status()
            translations = [t["text"].strip() for t in resp.json()["translations"]]
            if len(translations) != len(chunk):
                raise ValueError("unexpected number of translations")
            result.update({de: en for de, en in zip(chunk, translations) if en})
    except Exception as e:
        print(f"WARNING: DeepL failed, continuing without it: {e}")
    return result


def split_hours(hours):
    parts = SEPARATORS.split(hours)
    return [norm(p) if i % 2 == 0 else p for i, p in enumerate(parts)]


def summarize(lines):
    print("\n".join(lines))
    if path := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(path, "a", encoding="utf-8") as f:
            f.write("### Translations\n" + "\n".join(f"- {line}" for line in lines) + "\n")


def translate_features(features, out_path, cache_source=None, key=None):
    """Sets hoursEn/noteEn on the features and writes translations.json next to out_path."""
    props = [f["properties"] for f in features]
    hours = {p["hours"]: split_hours(p["hours"]) for p in props if p.get("hours")}
    strings = {s for parts in hours.values() for s in parts[::2] if s}
    strings |= {norm(p["note"]) for p in props if p.get("note") and norm(p["note"])}

    overrides = {norm(k): v for k, v in json.loads(OVERRIDES_PATH.read_text(encoding="utf-8")).items()}
    cache = load_entries(cache_source or os.environ.get("TRANSLATIONS_CACHE") or LIVE_TRANSLATIONS_URL)
    table = {s: overrides[s] for s in strings if s in overrides}
    cached = {s: cache[s] for s in strings if s not in table and s in cache}
    table.update(cached)
    misses = sorted(strings - table.keys())
    new = {}
    key = key if key is not None else os.environ.get("DEEPL_AUTH_KEY")
    if misses and key:
        new = deepl_translate(misses, key)
        table.update(new)
    elif misses:
        print("WARNING: DEEPL_AUTH_KEY not set, not translating new strings")

    for p in props:
        if p.get("note") and norm(p["note"]) in table:
            p["noteEn"] = table[norm(p["note"])]
        if p.get("hours"):
            parts = hours[p["hours"]]
            if all(s in table for s in parts[::2] if s):
                p["hoursEn"] = "".join(table[s] if i % 2 == 0 and s else s for i, s in enumerate(parts))

    generated = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    target = Path(out_path).parent / "translations.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        json.dumps({"generated": generated, "entries": dict(sorted(table.items()))}, separators=(",", ":"), ensure_ascii=False),
        encoding="utf-8",
    )
    unused = sorted(overrides.keys() - strings)
    for s in unused:
        print(f"WARNING: unused override (German text changed or gone?): {s!r}")
    summarize(
        [
            f"distinct strings: {len(strings)}",
            f"from overrides: {sum(s in overrides for s in table)}",
            f"cached: {len(cached)}",
            f"newly translated: {len(new)}",
            f"untranslated: {len(strings) - len(table)}",
            *(f"unused override: {s!r}" for s in unused),
        ]
    )
