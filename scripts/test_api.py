#!/usr/bin/env python3
"""
GTA VI Waiting Room — API test script.
Tests all public endpoints against the running backend.

Usage:
    python3 scripts/test_api.py [--base-url http://localhost:8080]
"""
import json
import sys
import time
from urllib.request import urlopen, Request
from urllib.error import URLError, HTTPError

BASE_URL = "http://localhost:8080"
errors = 0


def check(condition, message):
    """Assert a condition and record failure."""
    global errors
    if not condition:
        print(f"  ❌ FAIL: {message}")
        errors += 1
    return condition


def test(endpoint, description, validator=None):
    """Run a test against an API endpoint."""
    global errors
    url = f"{BASE_URL}{endpoint}"
    print(f"\n{'='*60}")
    print(f"TEST: {description}")
    print(f"GET {url}")

    try:
        req = Request(url, headers={"Accept": "application/json"})
        start = time.time()
        with urlopen(req, timeout=10) as resp:
            elapsed = (time.time() - start) * 1000
            body = resp.read().decode("utf-8")
            data = json.loads(body)
            print(f"  Status: {resp.status} ({elapsed:.0f}ms)")

            if validator:
                validator(resp.status, data)

            if errors == 0 or not validator:
                print(f"  ✅ PASS")
            return data
    except HTTPError as e:
        body = e.read().decode("utf-8") if e.fp else ""
        print(f"  Status: {e.code}")
        print(f"  Body: {body[:200]}")
        errors += 1
        print(f"  ❌ FAIL")
        return None
    except URLError as e:
        print(f"  ❌ CONNECTION ERROR: {e.reason}")
        errors += 1
        return None


def main():
    global errors

    # Health
    test("/api/health", "Health check", lambda s, d: (
        check(d.get("status") == "ok", "status should be 'ok'")
    ))

    # Game overview
    data = test("/api/v1/games/gta-vi", "Game overview", lambda s, d: (
        check(d.get("code") == "GTA_VI", "code should be GTA_VI"),
        check(d.get("name") == "Grand Theft Auto VI", "name should match"),
        check(d.get("release", {}).get("date") == "2026-11-19", "release date should be 2026-11-19"),
        check(d.get("release", {}).get("official") is True, "should be official"),
        check(len(d.get("trailers", [])) >= 2, f"trailers: {len(d.get('trailers', []))} (min 2)"),
        check(len(d.get("editions", [])) >= 2, f"editions: {len(d.get('editions', []))} (min 2)"),
        check(len(d.get("latestEvents", [])) >= 1, f"events: {len(d.get('latestEvents', []))} (min 1)"),
        check(d.get("systemStatus") is not None, "systemStatus should not be null"),
    ))

    # Latest trailer ordering
    if data:
        trailers = data.get("trailers", [])
        if trailers:
            first = trailers[0]
            print(f"\n  Latest trailer: {first['title']} ({first['mediaType']})")
            check("Trailer 2" in first["title"], "latest should be Trailer 2")

    # Trailers filtered
    data = test("/api/v1/games/gta-vi/trailers?type=TRAILER", "Trailers (TRAILER only)", lambda s, d: (
        check(len(d) >= 2, f"trailers: {len(d)} (min 2)"),
        check(all(t["mediaType"] == "TRAILER" for t in d), "all should be TRAILER type"),
    ))

    # Editions
    data = test("/api/v1/games/gta-vi/editions", "Editions list", lambda s, d: (
        check(len(d) >= 2, f"editions: {len(d)} (min 2)"),
        check(any(e["normalizedType"] == "STANDARD" for e in d), "should have STANDARD"),
        check(any(e["normalizedType"] == "ULTIMATE" for e in d), "should have ULTIMATE"),
        check(all(e["status"] == "PREORDER_AVAILABLE" for e in d), "all should be PREORDER_AVAILABLE"),
    ))

    # Events
    data = test("/api/v1/games/gta-vi/events?page=0&size=20", "Events timeline", lambda s, d: (
        check("events" in d, "should have 'events' key"),
        check("total" in d, "should have 'total' key"),
        check(len(d.get("events", [])) >= 1, "should have at least 1 event"),
    ))

    if data and len(data.get("events", [])) >= 2:
        events = data["events"]
        first_dt = events[0].get("detectedAt", "")
        second_dt = events[1].get("detectedAt", "")
        check(first_dt >= second_dt, f"events should be newest-first: {first_dt} vs {second_dt}")

    print(f"\n{'='*60}")
    print(f"RESULTS: {errors} error(s)")
    if errors:
        print("❌ Some tests failed!")
        sys.exit(1)
    else:
        print("✅ All tests passed!")


if __name__ == "__main__":
    if "--base-url" in sys.argv:
        idx = sys.argv.index("--base-url")
        BASE_URL = sys.argv[idx + 1]
    main()
