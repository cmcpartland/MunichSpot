"""Strict parser for simple OSM opening_hours. Recognises only "24/7" and "<days> <ranges>" rules joined by ";"; anything else is unknown (None).

Produces the same schedule rules as hours.py: {"from": min, "to": min, "days": [1..7] (absent = every day)}.
"""
import re

DAYS = {"mo": 1, "tu": 2, "we": 3, "th": 4, "fr": 5, "sa": 6, "su": 7}
DAY = "(?:Mo|Tu|We|Th|Fr|Sa|Su)"
DAY_ITEM = rf"{DAY}(?:-{DAY})?"
TIME = r"(\d{1,2}):(\d{2})"
RANGE = re.compile(rf"{TIME}-{TIME}")
RULE = re.compile(rf"^({DAY_ITEM}(?:,{DAY_ITEM})*) ({TIME}-{TIME}(?:,{TIME}-{TIME})*)$")


def minutes(h, m):
    h, m = int(h), int(m)
    return None if m > 59 or h > 24 or (h == 24 and m) else h * 60 + m


def parse_days(text):
    days = set()
    for item in text.split(","):
        a, _, b = item.partition("-")
        first = DAYS[a.lower()]
        last = DAYS[b.lower()] if b else first
        days.update(((first - 1 + i) % 7) + 1 for i in range((last - first) % 7 + 1))
    return days


def parse_opening_hours(text):
    text = (text or "").strip()
    if text == "24/7":
        return [{"from": 0, "to": 1440}]
    rules, seen = [], set()
    for part in text.split(";"):
        m = RULE.match(part.strip())
        if not m:
            return None
        days = parse_days(m.group(1))
        if days & seen:
            return None
        seen |= days
        for h1, m1, h2, m2 in RANGE.findall(m.group(2)):
            a, b = minutes(h1, m1), minutes(h2, m2)
            if a is None or b is None or a == b:
                return None
            rules.append({"from": a, "to": b if b > a else b + 1440, **({"days": sorted(days)} if len(days) < 7 else {})})
    return rules or None
