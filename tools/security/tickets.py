#!/usr/bin/env python3
"""Turn security findings into tracked GitHub Issues ("tickets").

Policy (docs/security/ticketing.md):
  secrets        Every leaked secret gets a ticket immediately, PR or not: once pushed to a
                 public repo it is compromised, and the fix is rotation, not deleting the commit.
                 Never auto-closed: only a human can confirm the credential was rotated.
  code-scanning  Open alerts on the default branch get a ticket (PR findings block the merge
                 instead). Tickets auto-close when the alert is fixed/dismissed and reopen if a
                 ticket was closed while its alert is still open.

Stdlib only, on purpose: no third-party deps means no SCA surface for the tooling itself.
Inputs come from the environment (GITHUB_TOKEN, GITHUB_REPOSITORY), never from ${{ }} in YAML.
"""

from __future__ import annotations

import argparse
import json
import os
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

def secret_findings(sarif_path: str):
    with open(sarif_path, encoding="utf-8") as f:
        sarif = json.load(f)
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


# --- code scanning alerts (SAST, default branch) -------------------------------------------

def severity(alert: dict) -> str:
    rule = alert.get("rule", {})
    sev = rule.get("security_severity_level") or {"error": "high", "warning": "medium"}.get(
        rule.get("severity"), "low")
    return sev if f"severity:{sev}" in LABELS else "low"


def ticket_code_scanning(gh: GitHub, ref: str) -> int:
    existing, changed = gh.tickets(), 0
    open_alerts = {}
    try:
        alerts = list(gh.paginate(f"/code-scanning/alerts?state=open&ref={ref}"))
    except urllib.error.HTTPError as e:
        if e.code != 404:  # 404 = no analysis uploaded for this ref yet -> nothing to ticket
            raise
        alerts = []
    for alert in alerts:
        key = f"code-scanning:{alert['number']}"
        open_alerts[key] = alert
        issue = existing.get(key)
        if issue and issue["state"] == "open":
            continue
        if issue:  # ticket closed by hand, but the alert is still open -> reopen
            gh.call("PATCH", f"/issues/{issue['number']}", {"state": "open"})
            gh.call("POST", f"/issues/{issue['number']}/comments",
                    {"body": f"Reopened: alert is still open on {code(ref)} ({run_link()})."})
            print(f"reopened: {key} -> #{issue['number']}")
            changed += 1
            continue
        rule, loc = alert["rule"], alert["most_recent_instance"]["location"]
        sev = severity(alert)
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

### Remediation
- [ ] Fix the code (or dismiss the alert in the Security tab **with a written reason**)
- [ ] This ticket closes automatically once the alert is fixed or dismissed

_Auto-generated by `tools/security/tickets.py`._
"""
        title = f"[sast] {rule.get('id', 'finding').split('.')[-1]} in {loc.get('path')}"
        issue = gh.call("POST", "/issues", {
            "title": title[:200], "body": body,
            "labels": [TICKET_LABEL, "sast", f"severity:{sev}"],
        })
        print(f"created: {key} -> #{issue.get('number', 'dry-run')}")
        changed += 1

    # Close the loop: open tickets whose alert is no longer open on this ref.
    for key, issue in existing.items():
        if key.startswith("code-scanning:") and issue["state"] == "open" and key not in open_alerts:
            alert = gh.call("GET", f"/code-scanning/alerts/{key.split(':')[1]}")
            gh.call("POST", f"/issues/{issue['number']}/comments",
                    {"body": f"Alert is now **{alert.get('state', 'closed')}**; closing ({run_link()})."})
            gh.call("PATCH", f"/issues/{issue['number']}",
                    {"state": "closed", "state_reason": "completed"})
            print(f"closed: {key} -> #{issue['number']}")
            changed += 1
    return changed


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--dry-run", action="store_true", help="print writes instead of doing them")
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("secrets", help="ticket gitleaks findings from a SARIF report")
    s.add_argument("sarif")
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
        n = ticket_secrets(gh, args.sarif) if args.cmd == "secrets" else ticket_code_scanning(gh, args.ref)
    except urllib.error.HTTPError as e:
        print(f"GitHub API error {e.code}: {e.read().decode(errors='replace')[:500]}", file=sys.stderr)
        return 1
    print(f"{n} ticket(s) created/updated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
