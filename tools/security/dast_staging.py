#!/usr/bin/env python3
"""DAST against staging after a deploy (ADR 0035). Policy: docs/security/dast.md

Active ZAP scans through the real edge (Caddy -> Next.js -> backend), only ever on the dedicated
scan trust:
  1. edge checks   TLS 1.2+ only, valid certificate, HTTP -> HTTPS, HSTS on both brands
  2. staff         active API scan as the scan trust's admin (staff host)
  3. devotee       active API scan of the portal as a scan-trust devotee (scan code, ADR 0035)
  4. anonymous     active API scan of the public endpoints (temple host) and registration
  5. frontend      ZAP baseline (crawl + passive rules) of both sites
  6. coverage      each session survived its own pass (checked right after it), and the staff
                   scan wrote devotees and donations; every problem is reported, not just the first

The spec comes from docs/api/openapi.json (the API contract): staging doesn't serve /v3/api-docs.

Env: DAST_TRUST, DAST_STAFF_EMAIL, DAST_STAFF_PASSWORD, DAST_DEVOTEE_OTP, STAFF_BASE_DOMAIN,
MANDIR_BASE_DOMAIN. Usage: dast_staging.py <output-dir>. Stdlib only.
"""
from __future__ import annotations

import json
import os
import pathlib
import socket
import ssl
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
ZAP_IMAGE = os.environ.get(
    "ZAP_IMAGE", "ghcr.io/zaproxy/zaproxy@sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef")  # 2.17.0
EXPECTED_TRUST_NAME = "DAST Scan Trust"
# Staging is one small box reached over the internet: few threads, polite timeouts, and hard caps.
# -T in zap-api-scan.py only bounds start-up and the passive scan; without the scanner caps one pass
# ran past 30 minutes (browser-driven DOM XSS and time-based SQLi rules are slow remotely).
ZAP_TUNING = ["-config", "scanner.threadPerHost=2", "-config", "connection.timeoutInSecs=30",
              "-config", "scanner.maxRuleDurationInMins=2", "-config", "scanner.maxScanDurationInMins=20"]
MAX_MINUTES = "10"


def fail(msg: str) -> None:
    print(f"::error::{msg}")
    sys.exit(1)


def problem(problems: list[str], msg: str) -> None:
    """A coverage problem: reported now, and the run fails at the end, so one can't hide another."""
    print(f"::error::{msg}")
    problems.append(msg)


def mask(value: str) -> None:
    if os.environ.get("GITHUB_ACTIONS") and value:
        print(f"::add-mask::{value}")


class Client:
    """Same-origin browser-like client: cookies by hand (Secure cookies, one host), CSRF echoed."""

    def __init__(self, host: str):
        self.base = f"https://{host}"
        self.cookies: dict[str, str] = {}

    def send(self, method: str, path: str, body=None, form=False):
        headers = {"Accept": "application/json"}
        if self.cookies:
            headers["Cookie"] = ";".join(f"{k}={v}" for k, v in self.cookies.items())
        if "XSRF-TOKEN" in self.cookies:
            headers["X-XSRF-TOKEN"] = self.cookies["XSRF-TOKEN"]
        data = None
        if body is not None:
            data = (urllib.parse.urlencode(body) if form else json.dumps(body)).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
        req = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                self._keep(r.headers)
                raw = r.read()
                status = r.status
        except urllib.error.HTTPError as e:
            self._keep(e.headers)
            raw, status = e.read(), e.code
        try:
            return status, json.loads(raw)
        except ValueError:
            return status, None

    def _keep(self, headers) -> None:
        for c in headers.get_all("Set-Cookie") or []:
            name, _, rest = c.partition("=")
            value = rest.split(";", 1)[0]
            if value:
                self.cookies[name.strip()] = value
            else:
                self.cookies.pop(name.strip(), None)

    def ok(self, method: str, path: str, body=None, form=False, expect=(200, 201, 202, 204)):
        status, payload = self.send(method, path, body, form)
        if status not in expect:
            fail(f"{method} {self.base}{path} -> {status}")
        return payload


def guard(staff_host: str, mandir_host: str) -> None:
    """Attack nothing but the scan trust on staging, whatever the inputs or DNS say."""
    for host in (staff_host, mandir_host):
        if not host.split(".", 1)[1].startswith("stg-"):
            fail(f"refusing to scan {host}: not a staging domain")
    temple = Client(mandir_host).ok("GET", "/api/v1/public/temple")
    if temple.get("trustName") != EXPECTED_TRUST_NAME:
        fail(f"refusing to scan {mandir_host}: it is not the DAST scan trust")


def edge_checks(hosts: list[str]) -> None:
    for host in hosts:
        ctx = ssl.create_default_context()  # verifies chain + hostname
        with socket.create_connection((host, 443), timeout=15) as s, ctx.wrap_socket(s, server_hostname=host) as t:
            print(f"{host}: {t.version()}, certificate verified")
        for old in (ssl.TLSVersion.TLSv1, ssl.TLSVersion.TLSv1_1):
            weak = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
            weak.check_hostname, weak.verify_mode = False, ssl.CERT_NONE
            try:
                weak.minimum_version = weak.maximum_version = old
                with socket.create_connection((host, 443), timeout=15) as s, weak.wrap_socket(s, server_hostname=host):
                    fail(f"{host} accepted {old.name}")
            except (ssl.SSLError, ValueError, OSError):
                pass  # refused (or not even offered by our OpenSSL): good
        r = urllib.request.Request(f"http://{host}/", method="GET")
        opener = urllib.request.build_opener(NoRedirect())
        try:
            opener.open(r, timeout=15)
            fail(f"http://{host}/ did not redirect to HTTPS")
        except urllib.error.HTTPError as e:
            loc = e.headers.get("Location", "")
            if e.code not in (301, 308) or not loc.startswith(f"https://{host}"):
                fail(f"http://{host}/ -> {e.code} {loc}: expected a permanent redirect to HTTPS")
        with urllib.request.urlopen(f"https://{host}/", timeout=30) as resp:
            hsts = resp.headers.get("Strict-Transport-Security", "")
        if "max-age=" not in hsts or int(hsts.split("max-age=")[1].split(";")[0]) < 15552000:
            fail(f"{host}: HSTS missing or shorter than 180 days ({hsts!r})")
        print(f"{host}: HTTP->HTTPS redirect, HSTS {hsts}")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def spec_for(host: str, keep, drop=(), examples=None) -> dict:
    spec = json.loads((ROOT / "docs/api/openapi.json").read_text())
    spec["servers"] = [{"url": f"https://{host}"}]
    spec["paths"] = {p: v for p, v in spec["paths"].items() if keep(p) and p not in drop}
    # ZAP builds each request body from the schema examples; a made-up id is a 404 every time.
    for (schema, prop), value in (examples or {}).items():
        spec["components"]["schemas"][schema]["properties"][prop]["example"] = value
    return spec


def replacer(rules: list[tuple[str, str, str]]) -> list[str]:
    # No spaces in values: zap-api-scan.py splits -z on whitespace (see dast.sh).
    out = []
    for i, (desc, header, value) in enumerate(rules):
        if " " in value:
            fail(f"replacer value for {header} contains a space")
        for k, v in (("description", desc), ("enabled", "true"), ("matchtype", "REQ_HEADER"),
                     ("matchstr", header), ("regex", "false"), ("replacement", value)):
            out += ["-config", f"replacer.full_list({i}).{k}={v}"]
    return out


def zap_api(out: pathlib.Path, name: str, spec: dict, rules: list[tuple[str, str, str]]) -> None:
    (out / f"openapi-{name}.json").write_text(json.dumps(spec))
    z = " ".join(ZAP_TUNING + replacer(rules))
    run_zap(out, name, ["zap-api-scan.py", "-t", f"/zap/wrk/openapi-{name}.json", "-f", "openapi",
                        "-c", "rules.tsv", "-T", MAX_MINUTES, "-z", z])


def run_zap(out: pathlib.Path, name: str, args: list[str]) -> None:
    print(f"--- ZAP {name}", flush=True)
    rc = subprocess.run(["docker", "run", "--rm", "-v", f"{out.resolve()}:/zap/wrk:rw", ZAP_IMAGE, *args,
                         "-J", f"{name}.json", "-r", f"{name}.html", "-I"]).returncode
    if rc > 1:
        fail(f"ZAP {name} failed (exit {rc})")
    if not (out / f"{name}.json").stat().st_size:
        fail(f"ZAP produced no report for {name}")


def staff_login(host: str) -> Client:
    c = Client(host)
    c.ok("GET", "/api/v1/csrf")
    c.ok("POST", "/api/v1/auth/login", {"email": os.environ["DAST_STAFF_EMAIL"],
                                        "password": os.environ["DAST_STAFF_PASSWORD"]}, form=True)
    c.ok("GET", "/api/v1/csrf")  # rotates on login
    c.ok("GET", "/api/v1/me")
    return c


def devotee_login(host: str, contact: str) -> Client:
    c = Client(host)
    c.ok("GET", "/api/v1/csrf")
    c.ok("POST", "/api/v1/public/devotee-login/code", {"channel": "EMAIL", "contact": contact})
    c.ok("POST", "/api/v1/public/devotee-login/verify",
         {"channel": "EMAIL", "contact": contact, "code": os.environ["DAST_DEVOTEE_OTP"]})
    c.ok("GET", "/api/v1/portal/me")
    return c


def donation_examples(staff: Client) -> dict[tuple[str, str], int]:
    """A real devotee and active fund of the scan trust, so the staff pass can record donations."""
    devotees = staff.ok("GET", "/api/v1/devotees?page=0&size=1")["items"]
    if not devotees:
        fail("the scan trust has no devotees: re-seed it (ADR 0035)")
    fund = next((f for f in staff.ok("GET", "/api/v1/donation-funds") if f["active"]), None) \
        or staff.ok("POST", "/api/v1/donation-funds", {"name": "DAST scan fund", "active": True})
    return {("RecordDonationRequest", "devoteeId"): devotees[0]["id"],
            ("RecordDonationRequest", "fundId"): fund["id"]}


def counts(staff: Client) -> dict[str, int]:
    return {"devotees": staff.ok("GET", "/api/v1/devotees?page=0&size=1")["total"],
            "donations": staff.ok("GET", "/api/v1/donations?page=0&size=1")["total"]}


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    out = pathlib.Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    out.chmod(0o777)  # ZAP's container runs as its own uid and writes reports here
    (out / "rules.tsv").write_text((ROOT / ".zap/rules.tsv").read_text())
    for k in ("DAST_STAFF_PASSWORD", "DAST_DEVOTEE_OTP"):
        mask(os.environ.get(k, ""))
    trust = os.environ["DAST_TRUST"]
    staff_host = f"{trust}.{os.environ['STAFF_BASE_DOMAIN']}"
    mandir_host = f"{trust}.{os.environ['MANDIR_BASE_DOMAIN']}"

    print("--- 0/6 target guard", flush=True)
    guard(staff_host, mandir_host)

    print("--- 1/6 edge: TLS, redirect, HSTS", flush=True)
    edge_checks([staff_host, mandir_host])

    problems: list[str] = []
    print("--- 2/6 staff (active, logged in as the scan trust's admin)", flush=True)
    staff = staff_login(staff_host)
    for v in staff.cookies.values():
        mask(v)
    before = counts(staff)
    examples = donation_examples(staff)
    session, xsrf = staff.cookies["SC_SESSION"], staff.cookies["XSRF-TOKEN"]
    # login/logout/register would rotate or kill the session or belong to the anonymous pass;
    # change-password would lock the scanner out; portal paths need the devotee cookie.
    zap_api(out, "zap-staff", spec_for(
        staff_host, lambda p: not p.startswith(("/api/v1/public/", "/api/v1/portal/")),
        drop=("/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/register", "/api/v1/profile/change-password"),
        examples=examples),
        [("csrf-header", "X-XSRF-TOKEN", xsrf), ("cookies", "Cookie", f"SC_SESSION={session};XSRF-TOKEN={xsrf}")])
    # Checked now, not after the other passes: sessions expire after 30 idle minutes, and the
    # devotee + anonymous passes take ~40, so a late check only measured the idle timeout.
    staff_alive = staff.send("GET", "/api/v1/me")[0]
    if staff_alive != 200:
        problem(problems, f"the staff session died during its scan ({staff_alive}): later requests were tested as anonymous")
        staff = staff_login(staff_host)
    after = counts(staff)
    created = {k: after[k] - before[k] for k in before}

    print("--- 3/6 devotee portal (active, scan-code login)", flush=True)
    devotee = devotee_login(mandir_host, f"devotee1@{trust}.example.com")
    for v in devotee.cookies.values():
        mask(v)
    dsession, dxsrf = devotee.cookies["SC_DEVOTEE"], devotee.cookies["XSRF-TOKEN"]
    zap_api(out, "zap-devotee", spec_for(mandir_host, lambda p: p.startswith("/api/v1/portal/"),
                                         drop=("/api/v1/portal/logout",)),
            [("csrf-header", "X-XSRF-TOKEN", dxsrf), ("cookies", "Cookie", f"SC_DEVOTEE={dsession};XSRF-TOKEN={dxsrf}")])
    devotee_alive = devotee.send("GET", "/api/v1/portal/me")[0]
    if devotee_alive != 200:
        problem(problems, f"the devotee session died during its scan ({devotee_alive})")

    print("--- 4/6 anonymous (active): public endpoints + registration", flush=True)
    anon = Client(mandir_host)
    anon.ok("GET", "/api/v1/csrf")
    axsrf = anon.cookies["XSRF-TOKEN"]
    zap_api(out, "zap-anon", spec_for(mandir_host, lambda p: p.startswith("/api/v1/public/")
                                      or p in ("/api/v1/register", "/api/v1/csrf", "/api/v1/ping")),
            [("csrf-header", "X-XSRF-TOKEN", axsrf), ("csrf-cookie", "Cookie", f"XSRF-TOKEN={axsrf}")])

    print("--- 5/6 frontend baseline: both sites", flush=True)
    for name, url in (("zap-web-staff", f"https://{staff_host}/login"), ("zap-web-temple", f"https://{mandir_host}/")):
        run_zap(out, name, ["zap-baseline.py", "-t", url, "-c", "rules.tsv", "-m", "3", "-z", " ".join(ZAP_TUNING)])

    print("--- 6/6 coverage", flush=True)
    print(f"staff session after its scan: {staff_alive} | devotee session after its scan: {devotee_alive}"
          f" | created: {created}")
    for k, n in created.items():
        if n < 1:
            problem(problems, f"the staff scan created no {k}: it never got past authz/CSRF/validation")
    (out / "coverage.json").write_text(json.dumps({"created": created}))
    if problems:
        return 1
    print("DAST staging scan complete")
    return 0


if __name__ == "__main__":
    sys.exit(main())
