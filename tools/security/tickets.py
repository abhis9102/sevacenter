#!/usr/bin/env python3
"""Turn security findings into tracked GitHub Issues ("tickets").

Policy (docs/security/ticketing.md):
  secrets        Every leaked secret gets a ticket immediately, PR or not: once pushed to a
                 public repo it is compromised, and the fix is rotation, not deleting the commit.
                 Never auto-closed: only a human can confirm the credential was rotated.
  dast           Unaccepted Low+ ZAP findings on the default branch get a ticket (Medium+
                 already blocks PRs). Auto-closes when a later scan of main no longer finds it.
  code-scanning  Open alerts on the default branch get a ticket (PR findings block the merge
                 instead): one per alert for SAST, SCA and IaC; one per CVE for container images,
                 and only once a fix exists (unfixable ones stay in the Security tab). Tickets
                 auto-close, with the reason, when nothing actionable is left and reopen if one
                 was closed by hand while still actionable. A scanner with no entry in TOOLS
                 fails the sync rather than being guessed.

Stdlib only, on purpose: no third-party deps means no SCA surface for the tooling itself.
Inputs come from the environment (GITHUB_TOKEN, GITHUB_REPOSITORY), never from ${{ }} in YAML.
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.request

API = os.environ.get("GITHUB_API_URL", "https://api.github.com")
TICKET_LABEL = "security-ticket"
MARKER = re.compile(r"<!-- sevacenter-ticket:(\S+) -->")
LABELS = {
    TICKET_LABEL: ("b60205", "Auto-generated from a security finding"),
    "secret-leak": ("d93f0b", "Leaked credential - rotate it"),
    "sast": ("5319e7", "Static analysis (code scanning) finding"),
    "sca": ("0e8a16", "Vulnerable dependency (code scanning) finding"),
    "image": ("006b75", "Container image finding with a fix available (Trivy)"),
    "iac": ("5319e7", "Infrastructure-as-code finding (Checkov)"),
    "dast": ("1d76db", "Runtime (DAST / ZAP) finding"),
    "severity:critical": ("b60205", ""),
    "severity:high": ("d93f0b", ""),
    "severity:medium": ("fbca04", ""),
    "severity:low": ("c5def5", ""),
}


class GitHub:
    def __init__(self, token: str, repo: str, dry_run: bool):
        self.token, self.repo, self.dry_run = token, repo, dry_run

    def call(self, method: str, path: str, body: dict | None = None):
        if self.dry_run and method != "GET":
            print(f"[dry-run] {method} {path} {json.dumps(body)[:200] if body else ''}")
            return {}
        req = urllib.request.Request(
            f"{API}/repos/{self.repo}{path}",
            method=method,
            data=json.dumps(body).encode() if body is not None else None,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else {}

    def paginate(self, path: str):
        page = 1
        sep = "&" if "?" in path else "?"
        while True:
            items = self.call("GET", f"{path}{sep}per_page=100&page={page}")
            if not items:
                return
            yield from items
            page += 1

    def ensure_labels(self):
        existing = {lbl["name"] for lbl in self.paginate("/labels")}
        for name, (color, desc) in LABELS.items():
            if name not in existing:
                self.call("POST", "/labels", {"name": name, "color": color, "description": desc})

    def tickets(self) -> dict[str, dict]:
        """Existing tickets (open or closed) keyed by their dedup marker."""
        found = {}
        for issue in self.paginate(f"/issues?state=all&labels={TICKET_LABEL}"):
            m = MARKER.search(issue.get("body") or "")
            if m and "pull_request" not in issue:
                found[m.group(1)] = issue
        return found


def code(value) -> str:
    """Render untrusted scanner output inertly: inside a code span, so `@user` can't ping
    anyone and markdown/HTML can't render. Backticks are stripped so the span can't be closed."""
    return "`" + str(value).replace("`", "'").replace("\n", " ")[:300] + "`"


def run_link() -> str:
    server, run = os.environ.get("GITHUB_SERVER_URL"), os.environ.get("GITHUB_RUN_ID")
    repo = os.environ.get("GITHUB_REPOSITORY")
    return f"{server}/{repo}/actions/runs/{run}" if server and run else "(local run)"


# --- secrets (gitleaks SARIF) ---------------------------------------------------------------

def sarif_files(path: str) -> list[pathlib.Path]:
    """A SARIF file, or every *.sarif under a directory. gitleaks-action stores its report
    under the runner's absolute path inside the artifact, so don't assume a layout."""
    p = pathlib.Path(path)
    files = sorted(p.rglob("*.sarif")) if p.is_dir() else [p]
    if not files or not files[0].is_file():
        raise FileNotFoundError(f"no SARIF report found at {path}")
    return files


def secret_findings(sarif_path: str):
    for file in sarif_files(sarif_path):
        yield from _secret_findings(json.loads(file.read_text(encoding="utf-8")))


def _secret_findings(sarif: dict):
    for run in sarif.get("runs", []):
        for res in run.get("results", []):
            loc = res["locations"][0]["physicalLocation"]
            fp = res.get("partialFingerprints", {})
            yield {
                "rule": res.get("ruleId", "unknown"),
                "file": loc["artifactLocation"]["uri"],
                "line": loc.get("region", {}).get("startLine", 0),
                "commit": fp.get("commitSha", "unknown"),
                "author": fp.get("author", "unknown"),
            }


def ticket_secrets(gh: GitHub, sarif_path: str) -> int:
    existing, created = gh.tickets(), 0
    ref = os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME", "?")
    for f in secret_findings(sarif_path):
        key = f"gitleaks:{f['commit'][:12]}:{f['file']}:{f['line']}:{f['rule']}"
        if key in existing:
            print(f"exists: {key} -> #{existing[key]['number']}")
            continue
        body = f"""<!-- sevacenter-ticket:{key} -->
## Leaked secret detected (gitleaks)

| | |
|---|---|
| Rule | {code(f['rule'])} |
| Location | {code(f"{f['file']}:{f['line']}")} |
| Commit | {code(f['commit'])} |
| Branch | {code(ref)} |
| Committed by | {code(f['author'])} |
| Detected in | {run_link()} |

The secret value is **redacted** here and in the CI log.

### Why this is a ticket even if the PR is never merged
The commit was pushed to a **public** repository, so the credential must be treated as
compromised right now. Removing the line, the commit or the branch does **not** un-leak it.

### Remediation (owner: whoever holds the credential)
- [ ] **Revoke / rotate** the credential at its issuer
- [ ] Check the issuer's access logs for use since the commit time
- [ ] Move the value to the environment / secrets manager; remove it from code
- [ ] Decide whether history rewrite is needed (it is *not* a substitute for rotation)
- [ ] Close this ticket with a note on what was rotated and when

_Auto-generated by `tools/security/tickets.py`; never auto-closed._
"""
        title = f"[secret] {f['rule']} in {f['file']}:{f['line']}"
        issue = gh.call("POST", "/issues", {
            "title": title[:200], "body": body,
            "labels": [TICKET_LABEL, "secret-leak", "severity:critical"],
        })
        created += 1
        print(f"created: {key} -> #{issue.get('number', 'dry-run')}")
    return created


# --- code scanning alerts (default branch) --------------------------------------------------

# Code scanning tool name (from the SARIF driver) -> (ticket kind/label, what to do about it).
# A tool that isn't listed fails the sync instead of being guessed: every scanner that uploads
# SARIF needs a deliberate ticketing decision (G6 Trivy alerts once all landed here as "sast").
TOOLS = {
    "Semgrep OSS": ("sast", "Fix the code, or dismiss the alert in the Security tab **with a written reason**"),
    "osv-scanner": ("sca", "Bump or override the dependency, or add an expiring entry to `osv-scanner.toml` "
                           "**with a written reason**"),
    "Trivy": ("image", "Rebuild on a patched base image (or bump the library), or add an expiring entry to "
                       "`.trivy/accepted.toml` **with a written reason**"),
    "Checkov": ("iac", "Fix the Terraform, or add an expiring entry to `.checkov/accepted.toml` "
                       "**with a written reason**"),
}
SEVERITY_ORDER = ["low", "medium", "high", "critical"]
FIXED_VERSION = re.compile(r"Fixed Version:[ \t]*(\S*)")
PACKAGE = re.compile(r"Package:[ \t]*(\S+)")


class UnknownTool(Exception):
    pass


def severity(alert: dict) -> str:
    rule = alert.get("rule", {})
    sev = rule.get("security_severity_level") or {"error": "high", "warning": "medium"}.get(
        rule.get("severity"), "low")
    return sev if f"severity:{sev}" in LABELS else "low"


def message(alert: dict) -> str:
    return (alert.get("most_recent_instance") or {}).get("message", {}).get("text", "")


def fixed_version(alert: dict) -> str:
    """Trivy writes 'Fixed Version: <v>' into the alert message, empty when no fix exists."""
    m = FIXED_VERSION.search(message(alert))
    return m.group(1) if m else ""


def alert_ticket(alert: dict, ref: str, kind: str, todo: str) -> tuple[str, dict]:
    """SAST / SCA / IaC: one ticket per alert."""
    key = f"code-scanning:{alert['number']}"
    rule, inst = alert["rule"], alert["most_recent_instance"]
    loc, sev = inst["location"], severity(alert)
    body = f"""<!-- sevacenter-ticket:{key} -->
## Code scanning alert #{alert['number']} ({alert['tool']['name']})

| | |
|---|---|
| Rule | {code(rule.get('id'))} |
| Severity | **{sev}** |
| Location | {code(f"{loc.get('path')}:{loc.get('start_line')}")} |
| Branch | {code(ref)} |
| Alert | {alert['html_url']} |

{code(rule.get('description', ''))}

{code(message(alert))}

### Remediation
- [ ] {todo}
- [ ] This ticket closes automatically once the alert is fixed or dismissed

_Auto-generated by `tools/security/tickets.py`._
"""
    title = f"[{kind}] {rule.get('id', 'finding').split('.')[-1]} in {loc.get('path')}"
    return key, {"title": title[:200], "body": body, "labels": [TICKET_LABEL, kind, f"severity:{sev}"]}


def image_ticket(cve: str, alerts: list[dict], ref: str, todo: str) -> tuple[str, dict]:
    """Image findings: one ticket per CVE, covering every package and image it's in (one base
    image rebuild usually fixes them all), and only once a fix exists (G6 policy)."""
    key = f"image:{cve}"
    sev = max((severity(a) for a in alerts), key=SEVERITY_ORDER.index)
    rows = []
    for a in sorted(alerts, key=lambda a: a["number"]):
        pkg = PACKAGE.search(message(a))
        where = a["most_recent_instance"]["location"].get("path")
        rows.append(f"| {code(pkg.group(1) if pkg else '?')} | {code(where)} | {code(fixed_version(a))} | "
                    f"{a['html_url']} |")
    images = sorted({a["most_recent_instance"]["location"].get("path") for a in alerts})
    body = f"""<!-- sevacenter-ticket:{key} -->
## {code(cve)} in the container images: a fix is available

| | |
|---|---|
| Severity | **{sev}** |
| Branch | {code(ref)} |

{code(alerts[0]['rule'].get('description', ''))}

| Package | Image | Fixed in | Alert |
|---|---|---|---|
{chr(10).join(rows)}

### Remediation
- [ ] {todo}
- [ ] This ticket closes automatically once no alert for this CVE has a fix pending

_Auto-generated by `tools/security/tickets.py`._
"""
    title = f"[image] {cve} in {', '.join(images)}"
    return key, {"title": title[:200], "body": body, "labels": [TICKET_LABEL, "image", f"severity:{sev}"]}


def ticket_code_scanning(gh: GitHub, ref: str) -> int:
    try:
        alerts = list(gh.paginate(f"/code-scanning/alerts?state=open&ref={ref}"))
    except urllib.error.HTTPError as e:
        if e.code != 404:  # 404 = no analysis uploaded for this ref yet -> nothing to ticket
            raise
        alerts = []

    # 1. The tickets that should exist right now.
    wanted, unknown, by_cve, unfixable, untouched = {}, set(), {}, set(), set()
    for alert in alerts:
        tool = alert["tool"]["name"]
        if tool not in TOOLS:
            unknown.add(tool)
            untouched.add(alert["number"])  # its existing ticket stays as it is
            continue
        kind, todo = TOOLS[tool]
        if kind != "image":
            key, ticket = alert_ticket(alert, ref, kind, todo)
            wanted[key] = ticket
        elif fixed_version(alert):
            by_cve.setdefault(alert["rule"]["id"], []).append(alert)
        else:
            unfixable.add(alert["number"])
    for cve, group in by_cve.items():
        key, ticket = image_ticket(cve, group, ref, TOOLS["Trivy"][1])
        wanted[key] = ticket
    print(f"{len(alerts)} open alert(s): {len(wanted)} ticket(s) wanted, "
          f"{len(unfixable)} image finding(s) with no fix yet (Security tab only)")

    # 2. Create them, or reopen one closed by hand while its finding is still open.
    existing, changed = gh.tickets(), 0
    for key, ticket in wanted.items():
        issue = existing.get(key)
        if issue and issue["state"] == "open":
            continue
        if issue:
            gh.call("PATCH", f"/issues/{issue['number']}", {"state": "open"})
            gh.call("POST", f"/issues/{issue['number']}/comments",
                    {"body": f"Reopened: still open on {code(ref)} ({run_link()})."})
            print(f"reopened: {key} -> #{issue['number']}")
        else:
            issue = gh.call("POST", "/issues", ticket)
            print(f"created: {key} -> #{issue.get('number', 'dry-run')}")
        changed += 1

    # 3. Close the rest, saying why: fixed/dismissed, or no longer ticketed by policy.
    for key, issue in existing.items():
        if issue["state"] != "open" or key in wanted:
            continue
        if key.startswith("image:"):
            why, reason = "No alert for this CVE has a fix pending any more", "completed"
        elif key.startswith("code-scanning:"):
            number = int(key.split(":")[1])
            if number in untouched:
                continue
            if number in unfixable:
                why, reason = ("No fix is available yet. Image findings without a fix stay in the Security tab "
                               "and are re-checked by the daily scan; a ticket opens automatically once a fix "
                               "ships (G6 policy, docs/security/ticketing.md)"), "not_planned"
            elif any(a["number"] == number for group in by_cve.values() for a in group):
                why, reason = "Now tracked in the per-CVE image ticket", "not_planned"
            else:
                state = gh.call("GET", f"/code-scanning/alerts/{number}").get("state", "closed")
                why, reason = f"Alert is now **{state}**", "completed"
        else:
            continue  # secret / DAST tickets have their own lifecycle
        gh.call("POST", f"/issues/{issue['number']}/comments", {"body": f"{why}; closing ({run_link()})."})
        gh.call("PATCH", f"/issues/{issue['number']}", {"state": "closed", "state_reason": reason})
        print(f"closed ({reason}): {key} -> #{issue['number']}")
        changed += 1

    if unknown:
        raise UnknownTool(f"code scanning alerts from tool(s) with no ticketing policy: {sorted(unknown)}. "
                          "Add them to TOOLS in tools/security/tickets.py (and docs/security/ticketing.md)")
    return changed


# --- DAST (ZAP findings from tools/security/dast_policy.py --out) ---------------------------

def ticket_dast(gh: GitHub, findings_path: str) -> int:
    with open(findings_path, encoding="utf-8") as f:
        current = {item["key"]: item for item in json.load(f)}
    existing, changed = gh.tickets(), 0
    for key, item in current.items():
        issue = existing.get(key)
        if issue and issue["state"] == "open":
            continue
        if issue:
            gh.call("PATCH", f"/issues/{issue['number']}", {"state": "open"})
            gh.call("POST", f"/issues/{issue['number']}/comments",
                    {"body": f"Reopened: the latest DAST scan of main still finds this ({run_link()})."})
            print(f"reopened: {key} -> #{issue['number']}")
            changed += 1
            continue
        sev = item["risk"] if f"severity:{item['risk']}" in LABELS else "low"
        body = f"""<!-- sevacenter-ticket:{key} -->
## DAST finding (ZAP): {code(item['name'])}

| | |
|---|---|
| Rule | {code(item['pluginId'])} (CWE-{item.get('cwe') or '?'}) |
| Risk | **{item['risk']}** (confidence {code(item.get('confidence'))}) |
| Request | {code(f"{item['method']} {item['path']}")} |
| Parameter | {code(item['param'] or '-')} |
| Attack | {code(item['attack'] or '-')} |
| Evidence | {code(item['evidence'] or '-')} |
| Detected in | {run_link()} (full HTML report in the `dast-report` artifact) |

{code(item['solution'])}

### Remediation
- [ ] Fix it and add a regression test (`SecurityRegressionTest`)
- [ ] Or accept it in `.zap/accepted.toml`, scoped to this rule + param/uri, **with a reason and
      an expiry**
- [ ] This ticket closes automatically once a scan of main no longer finds it

_Auto-generated by `tools/security/tickets.py`._
"""
        issue = gh.call("POST", "/issues", {
            "title": f"[dast] {item['name']} on {item['method']} {item['path']}"[:200], "body": body,
            "labels": [TICKET_LABEL, "dast", f"severity:{sev}"],
        })
        print(f"created: {key} -> #{issue.get('number', 'dry-run')}")
        changed += 1
    for key, issue in existing.items():
        if key.startswith("zap:") and issue["state"] == "open" and key not in current:
            gh.call("POST", f"/issues/{issue['number']}/comments",
                    {"body": f"No longer found by the DAST scan of main; closing ({run_link()})."})
            gh.call("PATCH", f"/issues/{issue['number']}", {"state": "closed", "state_reason": "completed"})
            print(f"closed: {key} -> #{issue['number']}")
            changed += 1
    return changed


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--dry-run", action="store_true", help="print writes instead of doing them")
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("secrets", help="ticket gitleaks findings from a SARIF report")
    s.add_argument("sarif", help="SARIF file, or a directory searched for *.sarif")
    d = sub.add_parser("dast", help="sync tickets with unaccepted ZAP findings")
    d.add_argument("findings", help="JSON written by dast_policy.py gate --out")
    c = sub.add_parser("code-scanning", help="sync tickets with open code scanning alerts")
    c.add_argument("--ref", default="refs/heads/main")
    args = p.parse_args()

    token, repo = os.environ.get("GITHUB_TOKEN"), os.environ.get("GITHUB_REPOSITORY")
    if not token or not repo:
        print("GITHUB_TOKEN and GITHUB_REPOSITORY must be set", file=sys.stderr)
        return 2
    gh = GitHub(token, repo, args.dry_run)
    try:
        gh.ensure_labels()
        if args.cmd == "secrets":
            n = ticket_secrets(gh, args.sarif)
        elif args.cmd == "dast":
            n = ticket_dast(gh, args.findings)
        else:
            n = ticket_code_scanning(gh, args.ref)
    except urllib.error.HTTPError as e:
        print(f"GitHub API error {e.code}: {e.read().decode(errors='replace')[:500]}", file=sys.stderr)
        return 1
    except UnknownTool as e:
        print(f"::error::{e}")
        return 1
    print(f"{n} ticket(s) created/updated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
