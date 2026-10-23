"""Run: python3 tools/data/test_osm_hours.py"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from osm_hours import parse_opening_hours


def R(a, b, days=None):
    return {"from": a, "to": b, **({"days": days} if days else {})}


CASES = {
    "Mo 10:30-19:00; Tu-Fr 08:00-18:00; Sa 07:30-15:00": [R(630, 1140, [1]), R(480, 1080, [2, 3, 4, 5]), R(450, 900, [6])],
    "24/7": [R(0, 1440)],
    "Mo-Su 08:00-20:00": [R(480, 1200)],
    "Mo,We,Fr 09:00-12:00,13:00-17:00": [R(540, 720, [1, 3, 5]), R(780, 1020, [1, 3, 5])],
    "Sa-Mo 10:00-12:00": [R(600, 720, [1, 6, 7])],
    "Fr 22:00-02:00": [R(1320, 1560, [5])],
    "Mo-Fr 08:00-24:00": [R(480, 1440, [1, 2, 3, 4, 5])],
    "Mo-Fr 08:00-12:00;Sa 09:00-10:00": [R(480, 720, [1, 2, 3, 4, 5]), R(540, 600, [6])],
    "Mo-Fr 08:00-18:00; Sa 08:00-12:00; PH off": None,
    "off \"Wegen größerer Sanierungsarbeiten geschlossen\"": None,
    "Mo-Fr 08:00-18:00; We 09:00-10:00": None,
    "Mo-Fr 08:00-18:00 \"nach Vereinbarung\"": None,
    "Mo-Fr sunrise-sunset": None,
    "Mo-Fr 08:00+": None,
    "Mar-Oct Mo-Fr 08:00-18:00": None,
    "Mo-Fr 08:00-18:00; SH off": None,
    "Mo-Fr 08:00-08:00": None,
    "Mo-Fr 25:00-26:00": None,
    "Mo-Fr 08:00-12:60": None,
    "Mo-Fr": None,
    "08:00-18:00": None,
    "off": None,
    "": None,
    None: None,
}

for text, expected in CASES.items():
    got = parse_opening_hours(text)
    assert got == expected, f"{text!r}: expected {expected}, got {got}"

print(f"ok: {len(CASES)} strings")
