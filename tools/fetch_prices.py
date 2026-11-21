#!/usr/bin/env python3
"""Download US average retail prices and merge them into data/grocery-prices.json,
the file the app reads.

Source: FRED (Federal Reserve Bank of St. Louis), which republishes the Bureau of
Labor Statistics average price series. Needs a free API key in FRED_API_KEY.

Format: {"<BLS series id>": [[days since 1970-01-01, price in USD], ...]}
"""
import datetime
import json
import os
import sys
import urllib.parse
import urllib.request

OUT = "data/grocery-prices.json"
API = "https://api.stlouisfed.org/fred/series/observations"
SERIES = [
    "APU0000703112", "APU0000709112", "APU0000708111", "APU0000706111", "APU0000FF1101",
    "APU0000715211", "APU0000701111", "APU0000702111", "APU0000701312", "APU0000FS1101",
    "APU0000704111", "APU0000717311", "APU0000711211", "APU000074714",
]
EPOCH = datetime.date(1970, 1, 1)


def fetch(series, key, start):
    query = urllib.parse.urlencode({
        "series_id": series, "api_key": key, "file_type": "json", "observation_start": start.isoformat(),
    })
    try:
        with urllib.request.urlopen(API + "?" + query, timeout=60) as r:
            rows = json.load(r)["observations"]
    except Exception as e:  # never print the URL: it contains the key
        sys.exit("Could not download %s: %s" % (series, type(e).__name__))
    points = {}
    for row in rows:
        try:
            value = float(row["value"])  # "." marks a month with no published figure
        except ValueError:
            continue
        if value > 0:
            points[(datetime.date.fromisoformat(row["date"]) - EPOCH).days] = value
    return points


def main():
    key = os.environ.get("FRED_API_KEY", "").strip()
    if not key:
        print("::warning::FRED_API_KEY is not set, so prices were not updated.")
        return

    today = datetime.date.today()
    start = datetime.date(today.year - 12, 1, 1)
    with open(OUT) as f:
        data = {k: {int(d): v for d, v in pts} for k, pts in json.load(f).items()}

    for series in SERIES:
        data.setdefault(series, {}).update(fetch(series, key, start))

    cutoff = (start - EPOCH).days
    out = {k: [[d, v] for d, v in sorted(pts.items()) if d >= cutoff] for k, pts in sorted(data.items())}
    newest = max(pts[-1][0] for pts in out.values() if pts)
    with open(OUT, "w") as f:
        json.dump(out, f, separators=(",", ":"))
    print("Newest month:", (EPOCH + datetime.timedelta(days=newest)).strftime("%B %Y"), "| series:", len(out))


if __name__ == "__main__":
    main()
