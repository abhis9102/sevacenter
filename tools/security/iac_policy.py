#!/usr/bin/env python3
"""IaC scan policy (G7): decides what blocks, on top of Checkov's JSON report.

  accepted  Every accepted risk in .checkov/accepted.toml needs one check ID, the Terraform
            resource address(es) it applies to, a written reason and an expiry no more than
            MAX_DAYS out. Same rules as the G3, G5 and G6 accepted risks.
  selftest  Checkov on the deliberately insecure fixture must report exactly the statuses listed
            in .checkov/fixtures/expected.txt. Proves the scanner, its version and our custom
            checks still catch what the gate relies on; a check that stops loading would
            otherwise look like clean infrastructure.
  gate      Fail on every failed check that isn't a valid accepted risk. Open-source Checkov
            reports no severities, so there's no "High and above" line to draw: everything
            blocks until it's fixed or explicitly accepted. Also fails on parsing errors (a file
            Checkov couldn't read wasn't scanned), on inline `checkov:skip` comments (acceptances
            belong in the reviewed, expiring file, not in a feature diff), and on a scan that saw
            Terraform files but no resources.

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
CHECK_ID = re.compile(r"^CKV2?_[A-Z0-9]+_\d+$")


def load_report(path: str) -> dict:
    """Checkov writes one object per framework (a list when several ran); we run only terraform.
    With nothing to scan it writes the summary alone, without "results"."""
    report = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    if isinstance(report, list):
        report = next((r for r in report if r.get("check_type") == "terraform"), {})
    return report


def results(report: dict, kind: str) -> list[dict]:
    return (report.get("results") or {}).get(kind) or []


def accepted(config: str, today: dt.date) -> tuple[list[str], list[dict]]:
    path = pathlib.Path(config)
    entries = []
    if path.exists():
        with path.open("rb") as f:
            entries = tomllib.load(f).get("accepted", [])
    errors, valid = [], []
    for e in entries:
        eid = e.get("id") or "<missing id>"
        resources = e.get("resources") or []
        reason = " ".join((e.get("reason") or "").split())
        until = e.get("ignoreUntil")
        problems = []
        if not CHECK_ID.match(eid):
            problems.append("id must be one Checkov check ID, e.g. CKV_AWS_144")
        if not resources:
            problems.append("must be scoped to the resource address(es) it applies to (resources)")
        if len(reason) < MIN_REASON:
            problems.append(f"reason missing or too short (min {MIN_REASON} chars)")
        if until is None:
            problems.append("no ignoreUntil. Every accepted risk must expire")
        else:
            until = until.date() if isinstance(until, dt.datetime) else until
            if until > today + dt.timedelta(days=MAX_DAYS):
                problems.append(f"ignoreUntil {until} is more than {MAX_DAYS} days out")
            elif until < today:
                problems.append(f"expired on {until}. Fix it or re-justify it with a new date")
        if problems:
            errors.extend(f"{config}: {eid}: {p}" for p in problems)
            continue  # an invalid entry suppresses nothing
        valid.append({"id": eid, "resources": set(resources), "used": False})
    print(f"{config}: {len(entries)} accepted risk(s) checked, {len(valid)} valid")
    return errors, valid


def selftest(report_path: str, expected_path: str) -> list[str]:
    report = load_report(report_path)
    actual = {}
    for kind, status in (("failed_checks", "FAILED"), ("passed_checks", "PASSED")):
        for r in results(report, kind):
            actual[(r["resource"], r["check_id"])] = status
    errors, cases = [], 0
    for raw in pathlib.Path(expected_path).read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].split()
        if not line:
            continue
        cases += 1
        status, resource, check_id = line
        got = actual.get((resource, check_id), "NOT REPORTED")
        if got != status:
            errors.append(f"self-test: expected {status} {resource} {check_id}, Checkov said {got}")
    print(f"self-test: {cases} expectation(s), {cases - len(errors)} met")
    if cases == 0:
        errors.append(f"self-test: no expectations in {expected_path}")
    return errors


def where(r: dict) -> str:
    path = (r.get("repo_file_path") or r.get("file_path") or "").lstrip("/")
    line = (r.get("file_line_range") or [1])[0]
    return f"file={path},line={line}"


def gate(report_path: str, config: str, scanned_dir: str, today: dt.date) -> list[str]:
    errors, valid = accepted(config, today)
    report = load_report(report_path)
    summary = report.get("summary") or report
    tf_files = sorted(pathlib.Path(scanned_dir).rglob("*.tf"))
    tf_files = [p for p in tf_files if ".terraform" not in p.parts]

    if summary.get("parsing_errors"):
        errors.append(f"{summary['parsing_errors']} Terraform file(s) could not be parsed, so they weren't scanned: "
                      + ", ".join(report.get("results", {}).get("parsing_errors", [])))
    if tf_files and not summary.get("resource_count"):
        errors.append(f"{len(tf_files)} .tf file(s) under {scanned_dir} but Checkov saw 0 resources: nothing was scanned")

    for r in results(report, "skipped_checks"):
        comment = (r.get("check_result") or {}).get("suppress_comment") or ""
        errors.append(f"::error {where(r)}::{r['check_id']} on {r['resource']} is skipped inline ({comment!r}). "
                      "Inline skips don't expire and hide in feature diffs: fix it, or add it to "
                      ".checkov/accepted.toml with a reason and an expiry")

    blocked = 0
    for r in results(report, "failed_checks"):
        match = next((a for a in valid if a["id"] == r["check_id"] and r["resource"] in a["resources"]), None)
        if match:
            match["used"] = True
            print(f"accepted: {r['check_id']} on {r['resource']}")
            continue
        blocked += 1
        hint = f" See {r['guideline']}" if r.get("guideline") else ""
        errors.append(f"::error {where(r)}::{r['check_id']} {r['resource']}: {r['check_name']}.{hint}")

    for a in valid:
        if not a["used"]:
            print(f"::warning::{config}: {a['id']} on {sorted(a['resources'])} matched nothing; remove it if it's fixed")
    print(f"IaC gate: {len(tf_files)} .tf file(s), {summary.get('resource_count', 0)} resource(s), "
          f"{summary.get('passed', 0)} passed, {blocked} failed and not accepted")
    return errors


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("accepted", help="accepted risks are scoped, reasoned and expiring")
    a.add_argument("config", nargs="?", default=".checkov/accepted.toml")
    s = sub.add_parser("selftest", help="the insecure fixture is reported exactly as expected")
    s.add_argument("report", help="checkov -o json output for the fixture")
    s.add_argument("expected", help="expected.txt: STATUS resource check_id per line")
    g = sub.add_parser("gate", help="fail on failed checks that aren't accepted")
    g.add_argument("report", help="checkov -o json output for the scanned directory")
    g.add_argument("--accepted", default=".checkov/accepted.toml")
    g.add_argument("--dir", required=True, help="the directory Checkov scanned (for the coverage check)")
    args = p.parse_args()

    today = dt.date.today()
    if args.cmd == "accepted":
        errors = accepted(args.config, today)[0]
    elif args.cmd == "selftest":
        errors = selftest(args.report, args.expected)
    else:
        errors = gate(args.report, args.accepted, args.dir, today)
    for e in errors:
        print(e if e.startswith("::") else f"::error::{e}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
