#!/usr/bin/env python3
"""DAST (ZAP) policy gate. Policy: docs/security/dast.md

  gate  Read ZAP's JSON report(s) (authenticated + unauthenticated pass), drop accepted risks (.zap/accepted.toml), then:
          Medium/High  -> fail (blocks the PR; the job is a required check)
          Low          -> warning here, ticket on main
          Informational -> ignored
        Accepted risks are scoped to a rule AND a parameter and/or URL pattern, never a whole
        rule (ignoring "cookie without HttpOnly" everywhere would also hide a future session
        cookie), and each needs a reason and an expiry <= 90 days, like osv-scanner.toml.

Writes the remaining Low+ findings to --out for tools/security/tickets.py.
Stdlib only.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
import tomllib
from urllib.parse import urlsplit

RISK = {0: "info", 1: "low", 2: "medium", 3: "high"}
LEVEL = {name: code for code, name in RISK.items()}
MAX_DAYS = 90
MIN_REASON = 20


def load_accepted(path: str, today: dt.date) -> tuple[list[dict], list[str]]:
    p = pathlib.Path(path)
    if not p.exists():
        return [], []
    with p.open("rb") as f:
        entries = tomllib.load(f).get("accepted", [])
    errors, live = [], []
    for e in entries:
        who = f"accepted[{e.get('pluginId', '?')}]"
        problems = []
        if not e.get("pluginId"):
            problems.append("pluginId is required")
        if not (e.get("param") or e.get("uri")):
            problems.append("scope it with param and/or uri; whole-rule ignores aren't allowed")
        if len((e.get("reason") or "").strip()) < MIN_REASON:
            problems.append(f"reason missing or too short (min {MIN_REASON} chars)")
        until = e.get("ignoreUntil")
        if until is None:
            problems.append("no ignoreUntil. Every accepted risk must expire")
        elif until > today + dt.timedelta(days=MAX_DAYS):
            problems.append(f"ignoreUntil {until} is more than {MAX_DAYS} days out")
        errors.extend(f"{who}: {msg}" for msg in problems)
        # An invalid or expired entry suppresses nothing: the finding stays visible.
        if not problems and until >= today:
            live.append(e)
    return live, errors


def accepted(alert: dict, inst: dict, rules: list[dict]) -> bool:
    for r in rules:
        if str(r["pluginId"]) != str(alert["pluginid"]):
            continue
        if r.get("param") and r["param"] != inst.get("param"):
            continue
        if r.get("uri") and not re.search(r["uri"], inst.get("uri", "")):
            continue
        return True
    return False


def findings(report: dict, rules: list[dict]) -> list[dict]:
    out = []
    for site in report.get("site", []):
        for a in site.get("alerts", []):
            risk = int(a.get("riskcode", 0))
            if risk == 0:
                continue
            for inst in a.get("instances", []):
                if accepted(a, inst, rules):
                    continue
                path = urlsplit(inst.get("uri", "")).path or "/"
                out.append({
                    "key": f"zap:{a['pluginid']}:{inst.get('method', '?')}:{path}:{inst.get('param', '')}",  # no spaces: ticket marker is \S+
                    "pluginId": a["pluginid"], "name": a.get("name", a.get("alert", "?")),
                    "risk": RISK[risk], "confidence": a.get("confidence"),
                    "method": inst.get("method"), "path": path, "param": inst.get("param", ""),
                    "attack": inst.get("attack", ""), "evidence": inst.get("evidence", ""),
                    "solution": re.sub(r"<[^>]+>", "", a.get("solution", ""))[:500],
                    "cwe": a.get("cweid"),
                })
    # one entry per key (ZAP can report the same instance more than once)
    return list({f["key"]: f for f in out}.values())


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    g = sub.add_parser("gate", help="fail on unaccepted findings at or above --fail-at")
    g.add_argument("zap_json", nargs="+", help="one or more ZAP JSON reports")
    g.add_argument("--accepted", default=".zap/accepted.toml")
    g.add_argument("--fail-at", choices=["low", "medium", "high"], default="medium")
    g.add_argument("--out", help="write remaining Low+ findings (JSON) for ticketing")
    args = p.parse_args()

    rules, errors = load_accepted(args.accepted, dt.date.today())
    merged: dict[str, dict] = {}
    for path in args.zap_json:
        with open(path, encoding="utf-8") as f:
            for item in findings(json.load(f), rules):
                merged.setdefault(item["key"], item)
    found = list(merged.values())
    threshold = LEVEL[args.fail_at]
    for item in sorted(found, key=lambda x: -LEVEL[x["risk"]]):
        msg = (f"[{item['risk']}] {item['name']} ({item['pluginId']}) on {item['method']} "
               f"{item['path']} param={item['param'] or '-'}")
        if LEVEL[item["risk"]] >= threshold:
            errors.append(msg)
        else:
            print(f"::warning::{msg}")
    if args.out:
        pathlib.Path(args.out).write_text(json.dumps(found, indent=2), encoding="utf-8")
    print(f"dast gate: {len(found)} unaccepted finding(s) (Low+), {len(rules)} accepted risk(s) "
          f"in force; failing at {args.fail_at}+")
    for e in errors:
        print(f"::error::{e}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
