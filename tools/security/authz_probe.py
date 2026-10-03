#!/usr/bin/env python3
"""Authorization probe (DAST, M1 slice 2c): cross-tenant and role checks against the running jar.

ZAP finds injection, headers and error handling. It can't find broken access control: a 200 for
GET /users/7 looks fine unless you know user 7 belongs to another tenant. This probe knows. It
seeds two tenants with admins, a leader and a member, then runs a matrix of
identity x endpoint x host and fails on any status other than the expected one. Every new
endpoint gets rows here (users: M1; devotees + CSV: M2; donations: M3).

Tenants are resolved from the real Host header (<slug>.sevacenter.app), as in production, not
from the dev-only X-Tenant-Slug override.

  authz_probe.py http://127.0.0.1:18080 [--out probe.json]

Stdlib only. Run it before ZAP: the per-IP login throttle allows few failed logins, and the
unauthenticated ZAP pass attacks the login endpoint.
"""

from __future__ import annotations

import argparse
import http.client
import json
import pathlib
import sys
import uuid
from urllib.parse import urlencode, urlsplit

BASE_DOMAIN = "sevacenter.app"
PASSWORD = "correct-horse-battery-staple"


class Client:
    """One browser: its own cookies (session + XSRF) and the Host it talks to."""

    def __init__(self, target: str, slug: str | None):
        u = urlsplit(target)
        self.netloc = u.netloc
        self.host = f"{slug}.{BASE_DOMAIN}" if slug else u.hostname
        self.cookies: dict[str, str] = {}

    def on(self, host: str) -> "Client":
        """The same cookies (session) sent to another host: a replayed or stolen session."""
        c = Client("http://" + self.netloc, None)
        c.host, c.cookies = host, self.cookies
        return c

    def request(self, method: str, path: str, body: dict | None = None, form: dict | None = None,
                csrf: bool = True, upload: tuple[str, str] | None = None) -> tuple[int, object]:
        headers = {"Host": self.host, "Accept": "application/json, text/csv"}
        data = None
        if body is not None:
            data, headers["Content-Type"] = json.dumps(body), "application/json"
        if form is not None:
            data, headers["Content-Type"] = urlencode(form), "application/x-www-form-urlencoded"
        if upload is not None:  # (field, csv text) as a multipart file
            boundary = uuid.uuid4().hex
            data = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{upload[0]}\"; filename=\"probe.csv\"\r\n"
                    f"Content-Type: text/csv\r\n\r\n{upload[1]}\r\n--{boundary}--\r\n").encode()
            headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
        if csrf and method != "GET":
            headers["X-XSRF-TOKEN"] = self.csrf_token()
        if self.cookies:
            headers["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        conn = http.client.HTTPConnection(self.netloc, timeout=30)
        try:
            conn.request(method, path, body=data, headers=headers)
            resp = conn.getresponse()
            raw = resp.read()
            for name, value in resp.getheaders():
                if name.lower() == "set-cookie":
                    k, _, v = value.split(";", 1)[0].partition("=")
                    if v:
                        self.cookies[k] = v
                    else:
                        self.cookies.pop(k, None)
        finally:
            conn.close()
        try:
            parsed = json.loads(raw) if raw else None
        except ValueError:
            parsed = raw.decode(errors="replace")
        return resp.status, parsed

    def csrf_token(self) -> str:
        if "XSRF-TOKEN" not in self.cookies:
            self.request("GET", "/api/v1/csrf")
        return self.cookies["XSRF-TOKEN"]

    def login(self, email: str, password: str = PASSWORD) -> int:
        status, _ = self.request("POST", "/api/v1/auth/login", form={"email": email, "password": password})
        self.cookies.pop("XSRF-TOKEN", None)  # rotated at login
        return status


class Probe:
    def __init__(self, target: str):
        self.target = target
        self.results: list[dict] = []

    def client(self, slug: str | None) -> Client:
        return Client(self.target, slug)

    def expect(self, name: str, want: int, got: tuple[int, object] | int) -> object:
        status, body = got if isinstance(got, tuple) else (got, None)
        ok = status == want
        self.results.append({"check": name, "expected": want, "actual": status, "ok": ok})
        print(f"  {'PASS' if ok else 'FAIL'}  {status} (want {want})  {name}")
        return body

    def check(self, name: str, ok: bool, detail: str = "") -> None:
        self.results.append({"check": name, "ok": ok, "detail": detail})
        print(f"  {'PASS' if ok else 'FAIL'}  {name}{'  ' + detail if detail and not ok else ''}")

    # --- seeding ------------------------------------------------------------------------------

    def tenant(self, prefix: str) -> tuple[str, Client]:
        slug = f"{prefix}-{uuid.uuid4().hex[:8]}"
        anon = self.client(None)
        status, body = anon.request("POST", "/api/v1/register", body={
            "slug": slug, "trustName": f"Probe {slug}", "adminEmail": admin(slug),
            "adminPassword": PASSWORD, "adminName": "Probe Admin"})
        if status != 201:
            sys.exit(f"seeding failed: register {slug} -> {status} {body}")
        return slug, self.session(slug, admin(slug))

    def session(self, slug: str, email: str) -> Client:
        c = self.client(slug)
        if (status := c.login(email)) != 200:
            sys.exit(f"seeding failed: login {email} on {slug} -> {status}")
        return c

    def staff(self, slug: str, admin_client: Client, email: str, role: str, activate: bool = True) -> tuple[int, str]:
        status, body = admin_client.request("POST", "/api/v1/users",
                                            body={"email": email, "displayName": "Probe", "role": role})
        if status != 201:
            sys.exit(f"seeding failed: create {email} -> {status} {body}")
        token = body["setupUrl"].split("#token=", 1)[1]
        if activate:
            status, _ = self.client(slug).request("POST", "/api/v1/auth/setup",
                                                  body={"token": token, "password": PASSWORD})
            if status != 204:
                sys.exit(f"seeding failed: setup {email} -> {status}")
        return body["user"]["id"], token

    # --- the matrix ---------------------------------------------------------------------------

    def run(self) -> bool:
        print("seeding two tenants (A, B) with admins, a leader and a member ...")
        a, admin_a = self.tenant("probe-a")
        b, admin_b = self.tenant("probe-b")
        self.staff(a, admin_a, "leader@probe.example", "LEADER")
        self.staff(a, admin_a, "member@probe.example", "MEMBER")
        leader = self.session(a, "leader@probe.example")
        member = self.session(a, "member@probe.example")
        victim_b, _ = self.staff(b, admin_b, "victim@probe.example", "MEMBER")
        pending_a, token_a = self.staff(a, admin_a, "pending@probe.example", "MEMBER", activate=False)
        new_user = {"email": "x@probe.example", "displayName": "X", "role": "MEMBER"}

        print("\nanonymous")
        anon = self.client(a)
        self.expect("anonymous cannot list users", 401, anon.request("GET", "/api/v1/users"))
        self.expect("anonymous cannot create users", 401, anon.request("POST", "/api/v1/users", body=new_user))

        print("\nroles within tenant A (invariant 5)")
        self.expect("member cannot list users", 403, member.request("GET", "/api/v1/users"))
        self.expect("member cannot create users", 403, member.request("POST", "/api/v1/users", body=new_user))
        self.expect("leader can list users", 200, leader.request("GET", "/api/v1/users"))
        self.expect("leader cannot create users", 403, leader.request("POST", "/api/v1/users", body=new_user))
        self.expect("leader cannot promote anyone", 403,
                    leader.request("PATCH", f"/api/v1/users/{pending_a}/role", body={"role": "TRUST_ADMIN"}))
        self.expect("leader cannot deactivate users", 403,
                    leader.request("POST", f"/api/v1/users/{pending_a}/deactivate"))
        self.expect("leader cannot issue setup links", 403,
                    leader.request("POST", f"/api/v1/users/{pending_a}/setup-link"))
        self.expect("member cannot promote themselves", 403,
                    member.request("PATCH", f"/api/v1/users/{pending_a}/role", body={"role": "TRUST_ADMIN"}))

        print("\ncross-tenant: A's admin against B's objects (BOLA)")
        self.expect("change role of B's user by id", 404,
                    admin_a.request("PATCH", f"/api/v1/users/{victim_b}/role", body={"role": "TRUST_ADMIN"}))
        self.expect("deactivate B's user by id", 404, admin_a.request("POST", f"/api/v1/users/{victim_b}/deactivate"))
        self.expect("issue a setup link for B's user", 404, admin_a.request("POST", f"/api/v1/users/{victim_b}/setup-link"))
        _, users_a = admin_a.request("GET", "/api/v1/users")
        _, users_b = admin_b.request("GET", "/api/v1/users")
        emails_a = {u["email"] for u in users_a}
        emails_b = {u["email"] for u in users_b}
        self.check("A's user list contains none of B's users", not emails_a & {admin(b), "victim@probe.example"},
                   f"leaked {emails_a & {admin(b), 'victim@probe.example'}}")
        self.check("B's user list contains none of A's users", not emails_b & {admin(a), "leader@probe.example"},
                   f"leaked {emails_b & {admin(a), 'leader@probe.example'}}")
        self.check("A's user list contains A's users", {admin(a), "leader@probe.example"} <= emails_a)

        print("\ncross-tenant: mass assignment")
        # Tenant ids aren't exposed, so this can't aim at B's exact id; UserManagementTest does.
        injected = dict(new_user, email="injected@probe.example", tenantId=1, tenant=b, status="ACTIVE",
                        passwordHash="{noop}" + PASSWORD)
        body = self.expect("create with tenant/status/passwordHash in the body", 201,
                           admin_a.request("POST", "/api/v1/users", body=injected))
        self.check("injected user is PENDING, not ACTIVE", (body or {}).get("user", {}).get("status") == "PENDING")
        _, users_a = admin_a.request("GET", "/api/v1/users")
        _, users_b = admin_b.request("GET", "/api/v1/users")
        self.check("injected user landed in the caller's tenant (A), not B",
                   "injected@probe.example" in {u["email"] for u in users_a}
                   and "injected@probe.example" not in {u["email"] for u in users_b})
        self.expect("injected password does not work", 401, self.client(a).login("injected@probe.example"))

        print("\ncross-tenant: setup links")
        self.expect("A's setup link redeemed on B's host", 400,
                    self.client(b).request("POST", "/api/v1/auth/setup", body={"token": token_a, "password": PASSWORD}))
        self.expect("A's setup link still works on A's host", 204,
                    self.client(a).request("POST", "/api/v1/auth/setup", body={"token": token_a, "password": PASSWORD}))

        print("\ndevotees (M2, ADR 0010): tiered access, masking, cross-tenant ids")
        devotee = {"fullName": "Probe Devotee", "phone": "9876543210", "email": "devotee@probe.example",
                   "addressLine": "1 Probe Road", "city": "Pune", "state": "Maharashtra", "pincode": "411001",
                   "dateOfBirth": "1980-05-14", "consentSource": "IN_PERSON"}
        self.expect("anonymous cannot list devotees", 401, anon.request("GET", "/api/v1/devotees"))
        self.expect("member cannot create devotees", 403, member.request("POST", "/api/v1/devotees", body=devotee))
        body = self.expect("leader creates a devotee", 201, leader.request("POST", "/api/v1/devotees", body=devotee))
        dev_a = (body or {}).get("id")
        status, seen = member.request("GET", f"/api/v1/devotees/{dev_a}")
        self.expect("member can view a devotee", 200, status)
        seen = seen if isinstance(seen, dict) else {}
        self.check("member sees phone/email masked, no address or birth date",
                   seen.get("masked") is True and "9876543210" not in json.dumps(seen)
                   and not seen.get("addressLine") and not seen.get("dateOfBirth"), json.dumps(seen)[:200])
        _, found = member.request("GET", "/api/v1/devotees?q=9876543210")
        self.check("member cannot search by phone", isinstance(found, dict) and found.get("total") == 0)
        self.expect("member cannot edit devotees", 403, member.request("PUT", f"/api/v1/devotees/{dev_a}", body=devotee))
        self.expect("member cannot erase devotees", 403, member.request("DELETE", f"/api/v1/devotees/{dev_a}"))
        self.expect("leader cannot erase devotees", 403, leader.request("DELETE", f"/api/v1/devotees/{dev_a}"))
        body = self.expect("B's admin creates a devotee", 201,
                           admin_b.request("POST", "/api/v1/devotees", body=dict(devotee, fullName="Tenant B Devotee")))
        dev_b = (body or {}).get("id")
        self.expect("A reads B's devotee by id", 404, admin_a.request("GET", f"/api/v1/devotees/{dev_b}"))
        self.expect("A edits B's devotee by id", 404,
                    admin_a.request("PUT", f"/api/v1/devotees/{dev_b}", body=dict(devotee, fullName="Hijacked")))
        self.expect("A erases B's devotee by id", 404, admin_a.request("DELETE", f"/api/v1/devotees/{dev_b}"))
        _, listed = admin_a.request("GET", "/api/v1/devotees?q=Tenant%20B")
        self.check("A's devotee search never returns B's devotees", isinstance(listed, dict) and listed.get("total") == 0)
        print("\ndevotee CSV export/import (M2): bulk PII is TRUST_ADMIN only")
        csv = ("fullName,phone,email,addressLine,city,state,pincode,dateOfBirth,consentSource\n"
               "Imported Devotee,9876543210,,,Pune,,,,IN_PERSON\n")
        self.expect("member cannot export devotees", 403, member.request("GET", "/api/v1/devotees/export"))
        self.expect("leader cannot export devotees", 403, leader.request("GET", "/api/v1/devotees/export"))
        self.expect("leader cannot import devotees", 403,
                    leader.request("POST", "/api/v1/devotees/import", upload=("file", csv)))
        self.expect("admin imports devotees", 200, admin_a.request("POST", "/api/v1/devotees/import", upload=("file", csv)))
        status, exported = admin_a.request("GET", "/api/v1/devotees/export")
        self.expect("admin exports devotees", 200, status)
        exported = exported if isinstance(exported, str) else ""
        self.check("A's export contains A's devotees and none of B's",
                   "Imported Devotee" in exported and "Tenant B Devotee" not in exported, exported[:200])
        self.check("exported phone cells can't run as formulas", "'+919876543210" in exported and "\n+91" not in exported
                   and ",+91" not in exported, exported[:200])
        self.expect("A's admin erases A's devotee", 204, admin_a.request("DELETE", f"/api/v1/devotees/{dev_a}"))

        print("\ndonations (M3.1, ADR 0011): append-only ledger, roles, tampering, cross-tenant ids")
        gift = {"donorName": "Probe Donor", "amount": "1500.50", "mode": "UPI", "receivedOn": "2026-04-14"}
        self.expect("member cannot record donations", 403, member.request("POST", "/api/v1/donations", body=gift))
        self.expect("member cannot list donations", 403, member.request("GET", "/api/v1/donations"))
        body = self.expect("leader records a donation", 201, leader.request("POST", "/api/v1/donations", body=gift))
        gift_a = (body or {}).get("id")
        self.check("amount is exact", isinstance(body, dict) and body.get("amount") == "1500.50", str(body)[:120])
        for bad in ("-1500", "0", "1e5", "0.001"):
            self.expect(f"amount {bad!r} is rejected", 400,
                        leader.request("POST", "/api/v1/donations", body=dict(gift, amount=bad)))
        self.expect("leader cannot reverse a donation", 403,
                    leader.request("POST", f"/api/v1/donations/{gift_a}/reverse", body={"reason": "probe reversal test"}))
        body = self.expect("B's admin records a donation", 201, admin_b.request("POST", "/api/v1/donations", body=gift))
        gift_b = (body or {}).get("id")
        self.expect("A reads B's donation by id", 404, admin_a.request("GET", f"/api/v1/donations/{gift_b}"))
        self.expect("A reverses B's donation", 404,
                    admin_a.request("POST", f"/api/v1/donations/{gift_b}/reverse", body={"reason": "cross-tenant reversal"}))
        self.expect("admin reverses A's donation", 201,
                    admin_a.request("POST", f"/api/v1/donations/{gift_a}/reverse", body={"reason": "probe reversal test"}))
        self.expect("a second reversal is refused", 409,
                    admin_a.request("POST", f"/api/v1/donations/{gift_a}/reverse", body={"reason": "probe reversal again"}))
        self.expect("no endpoint edits the ledger", 405,
                    admin_a.request("PUT", f"/api/v1/donations/{gift_a}", body=gift))

        print("\nCSRF")
        self.expect("state change without the CSRF header", 403,
                    admin_a.request("POST", "/api/v1/users", body=new_user, csrf=False))

        print("\ncross-tenant: sessions and hosts (run last: these destroy the session)")
        self.expect("A's credentials on B's host", 401, self.client(b).login(admin(a)))
        spoof = self.session(a, admin(a))
        self.expect("A's session with Host <a>.attacker.example", 401,
                    spoof.on(f"{a}.attacker.example").request("GET", "/api/v1/users"))
        replay = self.session(a, admin(a))
        self.expect("A's session replayed on B's host", 401, replay.on(f"{b}.{BASE_DOMAIN}").request("GET", "/api/v1/users"))
        self.expect("...and the session is destroyed, even back on A", 401, replay.request("GET", "/api/v1/me"))

        failed = [r for r in self.results if not r["ok"]]
        print(f"\nauthz probe: {len(self.results) - len(failed)}/{len(self.results)} checks passed")
        return not failed


def admin(slug: str) -> str:
    return f"admin@{slug}.example"


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("target", help="base URL of the app under test, e.g. http://127.0.0.1:18080")
    p.add_argument("--out", help="write the results (JSON)")
    args = p.parse_args()
    probe = Probe(args.target)
    ok = probe.run()
    if args.out:
        pathlib.Path(args.out).write_text(json.dumps(probe.results, indent=2), encoding="utf-8")
    for r in probe.results:
        if not r["ok"]:
            why = (f"expected {r['expected']}, got {r['actual']}" if "expected" in r else r.get("detail") or "check failed")
            print(f"::error::authz probe: {r['check']} ({why})")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
