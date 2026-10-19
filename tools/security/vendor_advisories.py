#!/usr/bin/env python3
"""Early warning from the vendor, before the vulnerability databases catch up.

osv-scanner (G3) and npm audit read the GitHub Advisory Database. A project's own advisory
(published on its repo) only reaches that database after GitHub reviews it, which can take days:
the 14 Oct 2026 Next.js advisories (two critical RCEs) were on vercel/next.js for two days while
G3 still passed next@16.3.8. Those first days are when exploitation starts. This reads the
vendors directly, for the few dependencies whose compromise would be the worst:

  npm   The installed version (package-lock.json) of each watched package, checked against the
        published advisories on its vendor repo (GET /repos/{repo}/security-advisories).
  selftest  The version matching, offline, on real vendor ranges (run before trusting a pass).
  node  A Node.js version (the one inside the runtime image), checked against nodejs.org's
        release index: if a newer release in the same major is marked as a security release,
        we are running a version with known fixes missing.

High/critical (or unrated) advisories fail; medium/low are printed as warnings. An accepted risk
in osv-scanner.toml (same id, unexpired) is honoured, so there is one list of exceptions.

Stdlib only (tomllib needs Python 3.11+). GITHUB_TOKEN, if set, avoids the anonymous rate limit.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import re
import sys
import tomllib
import urllib.request

# npm package -> the GitHub repo that publishes its advisories.
WATCH = {
    "next": "vercel/next.js",
    "react": "facebook/react",
    "react-dom": "facebook/react",
}
BLOCKING = {"critical", "high", None, ""}
NODE_INDEX = "https://nodejs.org/dist/index.json"
COMPARATOR = re.compile(r"(>=|<=|>|<|=)?\s*v?(\d+(?:\.\d+){0,2})")


def version(text: str) -> tuple[int, int, int]:
    """'16.4.1' / 'v22.23.3' -> (16, 4, 1); prerelease and build suffixes are ignored."""
    parts = [int(p) for p in re.findall(r"\d+", text.split("-")[0].split("+")[0])[:3]]
    return tuple(parts + [0] * (3 - len(parts)))  # type: ignore[return-value]


def in_range(v: tuple[int, int, int], spec: str) -> bool:
    """GitHub's vulnerable_version_range: comparators ANDed (space or comma separated),
    alternatives with '||'. A bare version means exactly that version. A list of lower bounds
    only (">= 13.3, >= 14, >=15, >=16", as the vendor wrote it) means "from the lowest one":
    read as AND it would match only >=16 and miss every vulnerable 13.x-15.x."""
    for alternative in spec.split("||"):
        comparators = COMPARATOR.findall(alternative)
        if not comparators:
            continue
        holds = [_holds(v, op or "=", version(x)) for op, x in comparators]
        lower_only = all(op in (">", ">=") for op, _ in comparators)
        if any(holds) if lower_only else all(holds):
            return True
    return False


def _holds(v, op, x) -> bool:
    return {"=": v == x, ">": v > x, ">=": v >= x, "<": v < x, "<=": v <= x}[op]


def patched(text: str | None) -> list[tuple[int, int, int]]:
    """'16.0.9, 15.5.8, 16.1.0-canary.17, 16.3.?' -> stable fixed versions. Prereleases are
    skipped; an unknown patch ('16.3.?') counts as fixed only above that line, never in it."""
    out = []
    for item in (text or "").split(","):
        item = item.strip()
        if not item or "-" in item:
            continue
        if "?" in item:
            major, minor = version(item.replace("?", "0"))[:2]
            out.append((major, minor, 10**6))
        else:
            out.append(version(item))
    return out


def affected(v: tuple[int, int, int], spec: str, fixed: list[tuple[int, int, int]]) -> bool:
    """Vendor-entered ranges are often just a lower bound (">=13.5.1") with the fix only in
    patched_versions, so the range alone would flag every later release. Safe when the
    installed version is at or past a fix on its own release line (major.minor), or past every
    fix (later lines carry it); otherwise the range decides."""
    if not in_range(v, spec):
        return False
    same_line = [p for p in fixed if p[:2] == v[:2]]
    if same_line:
        return v < min(same_line)
    return not (fixed and v > max(fixed))


def accepted(config: pathlib.Path, today: dt.date) -> set[str]:
    """Unexpired accepted-risk ids from osv-scanner.toml (sca_policy.py checks their reasons)."""
    if not config.exists():
        return set()
    entries = tomllib.loads(config.read_text(encoding="utf-8")).get("IgnoredVulns", [])
    return {e["id"] for e in entries if "ignoreUntil" not in e or e["ignoreUntil"] >= today}


def fetch(url: str):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "sevacenter-vendor-advisories"}
    token = os.environ.get("GITHUB_TOKEN")
    if token and url.startswith("https://api.github.com/"):
        headers["Authorization"] = f"Bearer {token}"
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as r:
        body = json.load(r)
        link = r.headers.get("Link", "")
    following = re.search(r'<([^>]+)>;\s*rel="next"', link)
    return body, following.group(1) if following else None


def repo_advisories(repo: str) -> list[dict]:
    url, out = f"https://api.github.com/repos/{repo}/security-advisories?state=published&per_page=100", []
    while url:
        page, url = fetch(url)
        out.extend(page)
    return out


def installed(lockfile: pathlib.Path) -> dict[str, str]:
    packages = json.loads(lockfile.read_text(encoding="utf-8")).get("packages", {})
    return {name: packages[f"node_modules/{name}"]["version"]
            for name in WATCH if f"node_modules/{name}" in packages}


def findings_npm(versions: dict[str, str], advisories: dict[str, list[dict]]) -> list[dict]:
    found = []
    for name, ver in sorted(versions.items()):
        for adv in advisories.get(WATCH[name], []):
            if adv.get("withdrawn_at"):
                continue
            for vuln in adv.get("vulnerabilities") or []:
                pkg = vuln.get("package") or {}
                if pkg.get("ecosystem") == "npm" and pkg.get("name") == name \
                        and affected(version(ver), vuln.get("vulnerable_version_range") or "",
                                     patched(vuln.get("patched_versions"))):
                    found.append({"id": adv["ghsa_id"], "cve": adv.get("cve_id"), "package": f"{name}@{ver}",
                                  "severity": adv.get("severity"), "summary": adv.get("summary", ""),
                                  "fixed": vuln.get("patched_versions"), "url": adv.get("html_url")})
    return found


def findings_node(running: str, releases: list[dict]) -> list[dict]:
    v = version(running)
    newer = [r for r in releases if version(r["version"])[0] == v[0] and version(r["version"]) > v]
    security = [r for r in newer if r.get("security")]
    if not security:
        return []
    latest = max(newer, key=lambda r: version(r["version"]))
    first = min(security, key=lambda r: version(r["version"]))
    return [{"id": f"node-{first['version']}", "cve": None, "package": f"node@{running.lstrip('v')}",
             "severity": "high", "summary": f"security release {first['version']} ({first['date']}) is newer",
             "fixed": latest["version"].lstrip("v"), "url": "https://nodejs.org/en/blog/vulnerability"}]


def report(found: list[dict], ok: set[str]) -> int:
    failed = 0
    for f in found:
        ids = {f["id"], f["cve"]} - {None}
        label = f"{f['package']}: {f['id']}{' / ' + f['cve'] if f['cve'] else ''} ({f['severity'] or 'unrated'}) " \
                f"{f['summary']} -> fixed in {f['fixed'] or '?'}  {f['url']}"
        if ids & ok:
            print(f"accepted  {label}")
        elif f["severity"] in BLOCKING:
            print(f"::error::{label}")
            failed += 1
        else:
            print(f"::warning::{label}")
    print(f"{len(found)} vendor advisories apply, {failed} blocking.")
    return 1 if failed else 0


# (installed, vulnerable_version_range, patched_versions, expected): real ranges from the
# vercel/next.js advisories, including the lower-bound-only ones that caused false positives.
SELFTEST = [
    ("16.3.8", "> 16.0.0 <= 16.4.0", "16.4.1", True),        # GHSA-2464: the one that matters
    ("16.4.1", "> 16.0.0 <= 16.4.0", "16.4.1", False),
    ("15.5.27", ">= 10.0.0  <= 15.5.27", "15.5.28", True),
    ("16.4.1", ">=13.5.1", "13.5.7", False),                 # lower bound only; fixed long ago
    ("13.5.3", ">=13.5.1", "13.5.7", True),
    ("15.5.8", ">= 13.3, >= 14, >=15, >=16", "16.0.9, 15.5.8, 16.1.0-canary.17", False),
    ("15.5.7", ">= 13.3, >= 14, >=15, >=16", "16.0.9, 15.5.8, 16.1.0-canary.17", True),
    ("16.3.8", ">= 16.0.0", "16.3.?", True),                 # fixed somewhere in 16.3: can't tell
    ("16.4.0", ">= 16.0.0", "16.3.?", False),
    ("14.2.9", "<14.2.10", "14.2.10", True),
    ("19.3.0", "= 19.2.0 || = 19.3.0", "19.3.1", True),
    ("19.3.1", "= 19.2.0 || = 19.3.0", "19.3.1", False),
]


def selftest() -> int:
    bad = [(i, r, p, want) for i, r, p, want in SELFTEST if affected(version(i), r, patched(p)) != want]
    nodes = [{"version": "v22.23.3", "date": "2026-09-23", "security": False},
             {"version": "v22.23.2", "date": "2026-07-28", "security": True},
             {"version": "v24.9.0", "date": "2026-09-01", "security": True}]
    if findings_node("v22.23.3", nodes) or not findings_node("v22.23.1", nodes):
        bad.append(("node", "security release in the same major", "", "detected"))
    for b in bad:
        print(f"::error::selftest: {b[0]} in {b[1]!r} (patched {b[2]!r}) should be {b[3]}")
    print(f"selftest: {len(SELFTEST) + 1 - len(bad)}/{len(SELFTEST) + 1} cases right")
    return 1 if bad else 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--accepted", default="osv-scanner.toml", type=pathlib.Path)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("npm").add_argument("lockfile", type=pathlib.Path)
    sub.add_parser("node").add_argument("version")
    sub.add_parser("selftest")
    args = ap.parse_args(argv)
    if args.cmd == "selftest":
        return selftest()
    ok = accepted(args.accepted, dt.date.today())
    if args.cmd == "npm":
        versions = installed(args.lockfile)
        missing = set(WATCH) - set(versions)
        if not versions:
            print(f"::error::none of the watched packages ({', '.join(WATCH)}) are in {args.lockfile}")
            return 2
        for m in sorted(missing):
            print(f"not installed, skipped: {m}")
        repos = {WATCH[n] for n in versions}
        return report(findings_npm(versions, {r: repo_advisories(r) for r in repos}), ok)
    releases, _ = fetch(NODE_INDEX)
    return report(findings_node(args.version, releases), ok)


if __name__ == "__main__":
    sys.exit(main())
