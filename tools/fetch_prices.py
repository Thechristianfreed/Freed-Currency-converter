#!/usr/bin/env python3
"""Download US average retail prices from the Bureau of Labor Statistics and
merge them into data/grocery-prices.json, the file the app reads.

Format: {"<BLS series id>": [[days since 1970-01-01, price in USD], ...]}
"""
import datetime
import json
import sys
import urllib.request

OUT = "data/grocery-prices.json"
API = "https://api.bls.gov/publicAPI/v1/timeseries/data/"
SERIES = [
    "APU0000703112", "APU0000709112", "APU0000708111", "APU0000706111", "APU0000FF1101",
    "APU0000715211", "APU0000701111", "APU0000702111", "APU0000701312", "APU0000FS1101",
    "APU0000704111", "APU0000717311", "APU0000711211", "APU000074714",
]
EPOCH = datetime.date(1970, 1, 1)


def main():
    year = datetime.date.today().year
    body = json.dumps({"seriesid": SERIES, "startyear": str(year - 9), "endyear": str(year)}).encode()
    req = urllib.request.Request(API, data=body, headers={
        "Content-Type": "application/json",
        "User-Agent": "currency-converter-price-updater (GitHub Actions)",
    })
    with urllib.request.urlopen(req, timeout=60) as r:
        reply = json.load(r)
    if reply.get("status") != "REQUEST_SUCCEEDED":
        sys.exit("BLS refused the request: %s" % reply.get("message"))

    with open(OUT) as f:
        data = {k: {int(d): v for d, v in pts} for k, pts in json.load(f).items()}

    newest = None
    for s in reply["Results"]["series"]:
        pts = data.setdefault(s["seriesID"], {})
        for row in s["data"]:
            period = row["period"]
            try:
                month, value = int(period[1:]), float(row["value"])
            except ValueError:
                continue  # "-" marks a month with no published figure
            if not period.startswith("M") or not 1 <= month <= 12 or value <= 0:
                continue
            day = datetime.date(int(row["year"]), month, 1)
            pts[(day - EPOCH).days] = value
            newest = max(newest or day, day)

    if newest is None:
        sys.exit("BLS returned no usable prices")
    cutoff = (datetime.date(year - 12, 1, 1) - EPOCH).days
    out = {k: [[d, v] for d, v in sorted(pts.items()) if d >= cutoff] for k, pts in sorted(data.items())}
    with open(OUT, "w") as f:
        json.dump(out, f, separators=(",", ":"))
    print("Newest month:", newest.strftime("%B %Y"), "| series:", len(out))


if __name__ == "__main__":
    main()
