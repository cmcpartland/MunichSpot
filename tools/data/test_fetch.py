"""Run: python3 tools/data/test_fetch.py"""
import json
import os
import sys
import tempfile
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))
import requests
import build_pois
from build_pois import fetch, overpass_elements

A, B = "https://a.example/api", "https://b.example/api"


def response(status=200, body=None):
    r = requests.Response()
    r.status_code = status
    r._content = json.dumps(body if body is not None else {}).encode()
    return r


def run(side_effect, urls, clock=None):
    with mock.patch("requests.request", side_effect=side_effect) as req, mock.patch("time.sleep") as sleep:
        with mock.patch("time.monotonic", side_effect=clock) if clock else mock.patch("time.monotonic", return_value=0):
            try:
                result = fetch(urls, {"data": "x"})
            except Exception as e:
                result = e
    return result, req, sleep


# connection error, then success from the second URL
result, req, sleep = run([requests.ConnectionError("unreachable"), response(body={"elements": [1]})], [A, B])
assert result == {"elements": [1]}, result
assert [c.args[1] for c in req.call_args_list] == [A, B]
assert req.call_args.kwargs["timeout"] == (15, 180)
assert sleep.call_count == 1

# a single URL string is retried on itself; 504 then a timeout then success
result, req, _ = run([response(504), requests.Timeout(), response(body={"ok": 1})], A)
assert result == {"ok": 1} and [c.args[1] for c in req.call_args_list] == [A, A, A]

# rotation wraps around
_, req, _ = run([requests.ConnectionError()] * 2 + [response(body={})], [A, B])
assert [c.args[1] for c in req.call_args_list] == [A, B, A]

# timeouts beyond the budget are re-raised
ticks = iter([0, 10_000])
result, req, sleep = run([requests.ConnectTimeout("slow")] * 3, [A, B], clock=lambda: next(ticks))
assert isinstance(result, requests.Timeout), result
assert req.call_count == 1 and sleep.call_count == 0

# non-retryable status raises immediately
result, req, sleep = run([response(400)], [A, B])
assert isinstance(result, requests.HTTPError) and req.call_count == 1 and sleep.call_count == 0


def elements_for(out, fetch_result, live):
    """Runs overpass_elements with a mocked Overpass fetch and live download."""
    with mock.patch.object(build_pois, "fetch", side_effect=fetch_result), mock.patch(
        "requests.get", side_effect=live
    ), mock.patch.dict(os.environ, {"LIVE_OSM_ELEMENTS_URL": "https://live.example/osm-elements.json"}):
        try:
            return overpass_elements(out)
        except SystemExit as e:
            return e


with tempfile.TemporaryDirectory() as tmp:
    out = Path(tmp) / "site" / "pois.geojson"
    saved = out.with_name("osm-elements.json")

    # success writes elements and the timestamp
    got = elements_for(out, [{"osm3s": {"timestamp_osm_base": "T1"}, "elements": [{"id": 1}]}], [])
    assert got == [{"id": 1}]
    assert json.loads(saved.read_text()) == {"timestamp": "T1", "elements": [{"id": 1}]}

    # Overpass down: reuse the live elements, warn, re-write them
    saved.unlink()
    live = response(body={"timestamp": "T0", "elements": [{"id": 9}]})
    with mock.patch("build_pois.warn") as warn:
        got = elements_for(out, requests.ConnectionError("down"), [live])
    assert got == [{"id": 9}]
    assert json.loads(saved.read_text()) == {"timestamp": "T0", "elements": [{"id": 9}]}
    assert warn.call_count == 1 and "T0" in warn.call_args.args[0] and "reused" in warn.call_args.args[0]

    # both down: exits with an error
    saved.unlink()
    got = elements_for(out, requests.ConnectionError("down"), [response(404)])
    assert isinstance(got, SystemExit) and "no earlier elements" in str(got), got
    assert not saved.exists()

print("ok")
