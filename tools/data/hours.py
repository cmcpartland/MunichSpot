"""Strict parser for the free-text toilet opening hours. Recognises only clear patterns; anything else is unknown (None).

A schedule is a list of rules {"from": min, "to": min, "days": [1..7] (ISO, absent = every day), "months": [1..12] (absent = all year)}.
"to" may exceed 1440 for ranges past midnight. A schedule always describes the whole opening times: outside its rules the toilet is closed.
"""
import re
from datetime import datetime, timedelta

WEEKDAYS = {"mo": 1, "di": 2, "mi": 3, "do": 4, "fr": 5, "sa": 6, "so": 7,
            "montags": 1, "dienstags": 2, "mittwochs": 3, "donnerstags": 4, "freitags": 5,
            "samstags": 6, "samtags": 6, "sonntags": 7}
MONTHS = {"jan": 1, "feb": 2, "mrz": 3, "mär": 3, "märz": 3, "mar": 3, "apr": 4, "mai": 5, "jun": 6,
          "jul": 7, "aug": 8, "sep": 9, "okt": 10, "nov": 11, "dez": 12, "januar": 1, "februar": 2, "april": 4,
          "juni": 6, "juli": 7, "august": 8, "september": 9, "oktober": 10, "november": 11, "dezember": 12}
ALWAYS = {"durchgehend", "24 stunden", "ganzjährig, ganztägig", "nicht bei hochwasser"}

TIME = r"(\d{1,2})(?::(\d{2}))?"
RANGE = re.compile(rf"{TIME}\s*(?:uhr)?\s*(?:-|bis)\s*{TIME}\s*(?:uhr)?")
PREFIX = re.compile(r"^(?:(?:ganzjährig|täglich)\s*,?\s*)+")
DAY_WORD = "|".join(sorted(WEEKDAYS, key=len, reverse=True))
DAY_SPEC = re.compile(rf"^(wochentags|{DAY_WORD})(?:\s*(?:-|bis)\s*({DAY_WORD}))?(?:/feiertags?)?:?\s+(.+)$")
MONTH_WORD = "|".join(sorted(MONTHS, key=len, reverse=True))
MONTH_SPEC = re.compile(rf"^({MONTH_WORD})\.?(?:\s*-\s*({MONTH_WORD})\.?)?:\s*(.+)$")


def minutes(h, m):
    h, m = int(h), int(m or 0)
    if m > 59 or h > 24 or (h == 24 and m):
        return None
    return h * 60 + m


def parse_ranges(text):
    """'8-12Uhr 13-15Uhr' -> [(480, 720), (780, 900)]; None unless the whole text is time ranges."""
    out, pos, text = [], 0, text.strip()
    while pos < len(text):
        m = RANGE.match(text, pos)
        if not m:
            return None
        a, b = minutes(*m.group(1, 2)), minutes(*m.group(3, 4))
        if a is None or b is None or a == b:
            return None
        out.append((a, b if b > a else b + 1440))
        pos = m.end()
        while pos < len(text) and text[pos] in " ,;":
            pos += 1
    return out or None


def cycle(a, b, size):
    n = (b - a) % size
    return [(a - 1 + i) % size + 1 for i in range(n + 1)]


def parse_part(part):
    s = re.sub(r"\s+", " ", part.strip().lower().replace("–", "-").replace("—", "-")).strip(" ,;")
    if s in ALWAYS:
        return [{"from": 0, "to": 1440}]
    if s.startswith("öffnungszeiten:"):
        rules = []
        for seg in s[len("öffnungszeiten:"):].split("|"):
            m = MONTH_SPEC.match(seg.strip())
            ranges = m and parse_ranges(m.group(3))
            if not ranges:
                return None
            months = cycle(MONTHS[m.group(1)], MONTHS[m.group(2) or m.group(1)], 12)
            rules += [{"from": a, "to": b, "months": sorted(months)} for a, b in ranges]
        return rules
    if m := re.fullmatch(rf"ganztägig, ({MONTH_WORD})\.? bis ({MONTH_WORD})\.?", s):
        return [{"from": 0, "to": 1440, "months": sorted(cycle(MONTHS[m.group(1)], MONTHS[m.group(2)], 12))}]
    s = PREFIX.sub("", s)
    if ranges := parse_ranges(s):
        return [{"from": a, "to": b} for a, b in ranges]
    rules = []
    for item in re.split(r"[,;]\s*(?=[a-zäöü])", s):
        m = DAY_SPEC.match(item.strip())
        ranges = m and parse_ranges(m.group(3))
        if not ranges:
            return None
        if m.group(1) == "wochentags":
            days = [1, 2, 3, 4, 5]
        else:
            days = cycle(WEEKDAYS[m.group(1)], WEEKDAYS[m.group(2) or m.group(1)], 7)
        rules += [{"from": a, "to": b, **({"days": sorted(days)} if len(days) < 7 else {})} for a, b in ranges]
    return rules


def parse_schedule(text):
    rules = []
    for part in text.split(" / "):
        parsed = parse_part(part)
        if not parsed:
            return None
        rules += [r for r in parsed if r not in rules]
    return rules


def is_open(schedule, at):
    """Whether the schedule is open at the local datetime, including spill past midnight from the previous day."""
    def covers(rule, when, minute):
        return (when.isoweekday() in rule.get("days", range(1, 8)) and when.month in rule.get("months", range(1, 13))
                and rule["from"] <= minute < rule["to"])
    minute = at.hour * 60 + at.minute
    prev = at - timedelta(days=1)
    return any(covers(r, at, minute) or covers(r, prev, minute + 1440) for r in schedule)
