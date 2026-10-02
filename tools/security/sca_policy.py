#!/usr/bin/env python3
"""SCA policy checks that the scanner itself doesn't enforce.

  coverage  Every <dependency> declared in each pom.xml must appear in osv-scanner's resolved
            package list. A scanner that silently fails to resolve part of the tree reports
            "0 vulnerabilities" for the part it never saw. This happened during the Boot 4
            evaluation: 15 of 183 packages were resolved, and the scan came back clean.
  ignores   Every accepted risk in osv-scanner.toml needs a written reason and an expiry no
            more than MAX_DAYS out. An ignore without an expiry is a permanent blind spot.

Stdlib only (tomllib needs Python 3.11+).
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
import tomllib

MAX_DAYS = 90
MIN_REASON = 20
DEP = re.compile(
    r"<dependency>\s*<groupId>([^<]+)</groupId>\s*<artifactId>([^<]+)</artifactId>", re.S)


def coverage(osv_json: str, poms: list[str]) -> list[str]:
    with open(osv_json, encoding="utf-8") as f:
        report = json.load(f)
    # A declared dependency also shows up as a version-less entry, so being in the list proves
    # nothing. It counts only once the scanner resolved it to a concrete version (and from
    # there, its transitive tree).
    resolved = {p["package"]["name"] for r in report.get("results", []) for p in r["packages"]
                if p["package"].get("version")}
    errors = []
    for pom in poms:
        text = re.sub(r"<!--.*?-->", "", pathlib.Path(pom).read_text(encoding="utf-8"), flags=re.S)
        declared = [f"{g.strip()}:{a.strip()}" for g, a in DEP.findall(text)]
        if not declared:
            errors.append(f"{pom}: no <dependency> entries found (parser or file problem?)")
        missing = [d for d in declared if d not in resolved]
        for d in missing:
            errors.append(f"{pom}: declared dependency {d} was not resolved by the scanner")
        print(f"{pom}: {len(declared) - len(missing)}/{len(declared)} declared dependencies "
              f"resolved; {len(resolved)} packages in the scanned tree")
    return errors


def ignores(config: str, today: dt.date) -> list[str]:
    path = pathlib.Path(config)
    if not path.exists():
        print(f"{config}: not present, so no accepted risks")
        return []
    with path.open("rb") as f:
        entries = tomllib.load(f).get("IgnoredVulns", [])
    errors = []
    for e in entries:
        vid = e.get("id", "<missing id>")
        reason = (e.get("reason") or "").strip()
        until = e.get("ignoreUntil")
        if len(reason) < MIN_REASON:
            errors.append(f"{vid}: reason missing or too short (min {MIN_REASON} chars)")
        if until is None:
            errors.append(f"{vid}: no ignoreUntil. Every accepted risk must expire")
            continue
        until = until.date() if isinstance(until, dt.datetime) else until
        if until > today + dt.timedelta(days=MAX_DAYS):
            errors.append(f"{vid}: ignoreUntil {until} is more than {MAX_DAYS} days out")
        elif until < today:
            # osv-scanner already stops ignoring it; this just keeps the file honest.
            errors.append(f"{vid}: expired on {until}. Fix it or re-justify it with a new date")
    print(f"{config}: {len(entries)} accepted risk(s) checked")
    return errors


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("coverage", help="every declared dependency was scanned")
    c.add_argument("osv_json", help="osv-scanner --format json --all-packages output")
    c.add_argument("poms", nargs="+")
    i = sub.add_parser("ignores", help="accepted risks have a reason and an expiry")
    i.add_argument("config", nargs="?", default="osv-scanner.toml")
    args = p.parse_args()

    errors = (coverage(args.osv_json, args.poms) if args.cmd == "coverage"
              else ignores(args.config, dt.date.today()))
    for e in errors:
        print(f"::error::{e}")  # GitHub Actions annotation; plain text locally
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
