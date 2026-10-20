"""Run: python3 tools/data/test_hours.py"""
import json
import sys
import tempfile
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from hours import colon_times, is_open, parse_schedule
from translate import translate_features


def R(a, b, days=None, months=None):
    return {"from": a, "to": b, **({"days": days} if days else {}), **({"months": months} if months else {})}


H = lambda h, m=0: h * 60 + m
DAILY_6_22 = [R(360, 1320)]
ALL_DAY = [R(0, 1440)]
CEMETERY = "Saisonal abhängig von Öffnungszeiten der städt. Friedhöfe"
MONTHLY = [R(480, 1020, months=[1, 2, 11, 12]), R(480, 1080, months=[3]), R(480, 1200, months=[4, 5, 6, 7, 8]), R(480, 1140, months=[9, 10])]
WEEK = [R(480, 1260, [1, 2, 3, 4, 5]), R(540, 1200, [6]), R(600, 1200, [7])]

CASES = {
    "Täglich 06:00 Uhr bis 24:00 Uhr": [R(360, 1440)],
    "nicht bei Hochwasser": ALL_DAY,
    "ganzjährig täglich 06:00 -22:00 Uhr": DAILY_6_22,
    "Täglich, 06:00-22:00 Uhr": DAILY_6_22,
    "06:00 - 22:00 Uhr": DAILY_6_22,
    "Täglich 08:00 Uhr bis 17:00 Uhr": [R(480, 1020)],
    "ganzjährig täglich 06:00 -02:00 Uhr": [R(360, 1560)],
    "Durchgehend": ALL_DAY,
    "Öffnungszeiten: Nov.-Feb.: 8-17 Uhr | Mrz.: 8-18 Uhr | Apr.-Aug.: 8-20 Uhr | Sep.-Okt.: 8-19 Uhr": MONTHLY,
    "ganzjährig täglich 06:00 - 22:00 Uhr": DAILY_6_22,
    "Täglich 00:00 Uhr bis 24:00 Uhr": ALL_DAY,
    "Täglich, Nov-Feb bis 17 Uhr, im März bis 18:00 Uhr. Apr-Aug bis 20:00 Uhr, Sep-Okt bis 19 Uhr": None,
    "Täglich, 00:00-24:00 Uhr": ALL_DAY,
    "ganzjährig": None,
    CEMETERY + " / Saisonal abhängig von Öffnungszeiten der Friedhöfe": None,
    "Täglich währen der Öffnungszeiten des Friedhofes": None,
    "Mo-Sa: 07:00-17:45 Uhr": [R(420, 1065, [1, 2, 3, 4, 5, 6])],
    "Täglich 08:00 Uhr bis 16:00 Uhr / Wie Friedhof": None,
    "24 Stunden": ALL_DAY,
    "ganzjährig, ganztägig": ALL_DAY,
    "Täglich 06:00 Uhr bis 24:00 Uhr / Täglich 00:00 Uhr bis 24:00 Uhr": [R(360, 1440), R(0, 1440)],
    "Mo-Fr 08:00 Uhr bis 21:00 Uhr, Sa 09:00 Uhr bis 20:00 Uhr, So/Feiertag 10:00 Uhr bis 20:00 Uhr"
    " / wochentags 8:00 Uhr bis 21:00 Uhr, samtags 9:00 Uhr bis 20:00 Uhr, sonntags/feiertags 10:00 bis 20:00 Uhr": WEEK,
    "ganztägig, Mai bis Oktober": [R(0, 1440, months=[5, 6, 7, 8, 9, 10])],
    "ganzjährig, während der Öffnungszeiten des Kiosk": None,
    "Ganzjährig, Täglich, 00:00-24:00 Uhr": ALL_DAY,
    "Ganzjährig, während der Öffnungszeiten der Gastronomie": None,
    "Mai – Oktober": None,
    "Täglich, 06:00-24:00 Uhr": [R(360, 1440)],
    "Mai bis Oktober": None,
    "nur bei Veranstaltungen": None,
    "während der Öffnungszeiten des Cafe`s": None,
    "Mai - Oktober": None,
    "Täglich, Mo-Do 8-12Uhr 13-15Uhr, Fr 8-12Uhr": [R(480, 720, [1, 2, 3, 4]), R(780, 900, [1, 2, 3, 4]), R(480, 720, [5])],
    CEMETERY + " / Abhängig von saisonalen Öffnungszeiten des Friedhofs / Saisonal mit FH Öffnungszeiten": None,
    "Täglich ab 8 Uhr Schließung je nach Friedhofsöffnungszeien": None,
    "8 bis 20 Uhr": [R(480, 1200)],
    "Täglich ab 8 Uhr Schließung je Friedhofsöffnungszeiten": None,
    "s. Friedhofsöffnungszeiten": None,
    "Durchgehend / Täglich, 00:00-24:00 Uhr": ALL_DAY,
    "täglich 12:00 Uhr bis 22:00 Uhr": [R(720, 1320)],
    "samstags 12:00 Uhr bis 3:00 Uhr; sonntags 12:00 Uhr bis 1:00 Uhr; wochentags 16:00 Uhr bis 1:00 Uhr":
    [R(720, 1620, [6]), R(720, 1500, [7]), R(960, 1500, [1, 2, 3, 4, 5])],
    "dienstags bis sonntags 11:00 Uhr bis 23:00 Uhr": [R(660, 1380, [2, 3, 4, 5, 6, 7])],
    "Täglich 08:00 Uhr bis 20:00 Uhr": [R(480, 1200)],
    "ganzjährig, täglich, 06:00- 22:00 Uhr,": DAILY_6_22,
    "ganzjährig täglich 06:00 - 22:00 Uhr / Täglich 06:00 Uhr bis 2:00 Uhr": [R(360, 1320), R(360, 1560)],
    "Täglich, 08:00-20:00 Uhr": [R(480, 1200)],
    CEMETERY: None,
    "": None,
    "Täglich 25:00 Uhr bis 26:00 Uhr": None,
}

for text, expected in CASES.items():
    got = parse_schedule(text)
    assert got == expected, f"{text!r}: expected {expected}, got {got}"


def at(y, mo, d, h, mi=0):
    return datetime(y, mo, d, h, mi)


# 2026-10-17 is a Saturday, 2026-10-18 a Sunday, 2026-10-19 a Monday.
night = parse_schedule("samstags 12:00 Uhr bis 3:00 Uhr; sonntags 12:00 Uhr bis 1:00 Uhr; wochentags 16:00 Uhr bis 1:00 Uhr")
assert is_open(night, at(2026, 10, 18, 0, 30))
assert is_open(night, at(2026, 10, 18, 2, 59))
assert not is_open(night, at(2026, 10, 18, 3, 0))
assert not is_open(night, at(2026, 10, 18, 11, 59))
assert is_open(night, at(2026, 10, 19, 0, 30))
assert not is_open(night, at(2026, 10, 19, 1, 0))
assert not is_open(night, at(2026, 10, 19, 15, 59))
assert is_open(night, at(2026, 10, 19, 16, 0))

late = parse_schedule("ganzjährig täglich 06:00 -02:00 Uhr")
assert is_open(late, at(2026, 10, 19, 1, 59)) and not is_open(late, at(2026, 10, 19, 2, 0))
assert not is_open(late, at(2026, 10, 19, 5, 59)) and is_open(late, at(2026, 10, 19, 6, 0))

season = parse_schedule("ganztägig, Mai bis Oktober")
assert is_open(season, at(2026, 6, 1, 3)) and not is_open(season, at(2026, 11, 1, 12))
assert is_open(season, at(2026, 11, 1, 0, 0)) is False

months = parse_schedule("Öffnungszeiten: Nov.-Feb.: 8-17 Uhr | Mrz.: 8-18 Uhr | Apr.-Aug.: 8-20 Uhr | Sep.-Okt.: 8-19 Uhr")
assert is_open(months, at(2026, 3, 10, 17, 30)) and not is_open(months, at(2026, 2, 10, 17, 30))
assert is_open(months, at(2026, 7, 10, 19, 59)) and not is_open(months, at(2026, 9, 10, 19, 0))

# Month rules at the month boundary use the previous day's month for spill past midnight.
spill = [{"from": 1200, "to": 1560, "months": [10]}]
assert is_open(spill, at(2026, 11, 1, 0, 30)) and not is_open(spill, at(2026, 11, 1, 2, 30))
assert colon_times("11.00 am to 11.00 pm") == "11:00 am to 11:00 pm"
assert colon_times("8.00-20.00") == "8:00-20:00"
assert colon_times("1.5 km, 24/7, Nr. 12, 123.45, 8:00") == "1.5 km, 24/7, Nr. 12, 123.45, 8:00"

with tempfile.TemporaryDirectory() as tmp:
    cache = Path(tmp) / "cache.json"
    cache.write_text(json.dumps({"entries": {"täglich 11 bis 23 Uhr": "Daily, 11.00 am to 11.00 pm"}}), encoding="utf-8")
    feats = [{"properties": {"hours": "täglich 11 bis 23 Uhr", "note": "Hinweis 8.00"}}]
    translate_features(feats, Path(tmp) / "pois.json", cache_source=str(cache), key="")
    assert feats[0]["properties"]["hoursEn"] == "Daily, 11:00 am to 11:00 pm", feats
    assert "noteEn" not in feats[0]["properties"]

print(f"ok: {len(CASES)} strings")
