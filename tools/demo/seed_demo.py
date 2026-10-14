#!/usr/bin/env python3
"""Fill a trust with realistic demo data through the public API.

Everything goes through the same endpoints the staff app uses, so encryption (PAN), row-level
security, validation, receipt numbering and the audit trail all run exactly as for real data.
Nothing here touches the database directly.

All people, phone numbers and PANs are fictional; emails use the reserved example.com domain.
Never run this against a production trust.

    SEED_EMAIL=admin@demo.example SEED_PASSWORD=... \\
        python3 tools/demo/seed_demo.py --base https://demo.stg-seva.example.in

--host overrides the Host header, to reach the app through a plain-http tunnel before DNS points
at it (--base http://localhost:13000 --host demo.stg-seva.example.in). Re-running is safe: funds, pujas, priests, teams and events are matched by name and
skipped; devotees, donations, bookings and sevaks are only added when the trust has none, unless --force.
"""
import argparse
import datetime as dt
import json
import os
import random
import sys
import urllib.error
import urllib.parse
import urllib.request

IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
TODAY = dt.date.today()
rnd = random.Random(108)

FIRST = ["Aarav", "Vihaan", "Ananya", "Diya", "Rohan", "Saanvi", "Arjun", "Isha", "Kavya", "Aditya",
         "Meera", "Krishna", "Lakshmi", "Govind", "Radha", "Suresh", "Sunita", "Ramesh", "Priya", "Vikram",
         "Neha", "Rahul", "Pooja", "Sanjay", "Anjali", "Mahesh", "Geeta", "Venkatesh", "Padma", "Harish"]
LAST = ["Sharma", "Iyer", "Patil", "Deshpande", "Kulkarni", "Reddy", "Nair", "Joshi", "Gupta", "Agarwal",
        "Rao", "Menon", "Bhat", "Mishra", "Pandey", "Chaturvedi", "Kamath", "Shetty", "Verma", "Trivedi"]
PLACES = [("Pune", "Maharashtra", "4110"), ("Mumbai", "Maharashtra", "4000"), ("Bengaluru", "Karnataka", "5600"),
          ("Chennai", "Tamil Nadu", "6000"), ("Hyderabad", "Telangana", "5000"), ("Varanasi", "Uttar Pradesh", "2210")]
STREETS = ["Shivaji Nagar", "MG Road", "Gandhi Chowk", "Temple Street", "Nehru Colony", "Laxmi Road", "Station Road"]
GOTRAS = ["Kashyap", "Bharadwaj", "Vashishtha", "Atri", "Gautam", "Vishwamitra", "Jamadagni"]
NAKSHATRAS = ["Ashwini", "Rohini", "Pushya", "Magha", "Hasta", "Chitra", "Swati", "Anuradha", "Shravana", "Revati"]
RASHIS = ["Mesha", "Vrishabha", "Mithuna", "Karka", "Simha", "Kanya", "Tula", "Vrishchika", "Dhanu", "Makara"]
PAN_LETTERS = "ABCDEFGHJKLMNPRSTUVWXYZ"


class Api:
    def __init__(self, base, host):
        self.base = base.rstrip("/")
        self.host = host
        self.cookies = {}

    def _send(self, method, path, body=None, form=False):
        headers = {"Accept": "application/json"}
        if self.host:
            headers["Host"] = self.host
        if self.cookies:
            headers["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        if "XSRF-TOKEN" in self.cookies:
            headers["X-XSRF-TOKEN"] = self.cookies["XSRF-TOKEN"]
        data = None
        if body is not None:
            if form:
                data = urllib.parse.urlencode(body).encode()
                headers["Content-Type"] = "application/x-www-form-urlencoded"
            else:
                data = json.dumps(body).encode()
                headers["Content-Type"] = "application/json"
        req = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                self._keep_cookies(resp.headers)
                raw = resp.read()
                try:
                    return resp.status, json.loads(raw)
                except ValueError:
                    return resp.status, None
        except urllib.error.HTTPError as e:
            self._keep_cookies(e.headers)
            return e.code, e.read().decode(errors="replace")[:400]

    def _keep_cookies(self, headers):
        # Handled by hand: the session cookie is Secure, which a cookie jar won't send over a
        # plain-http tunnel.
        for c in headers.get_all("Set-Cookie") or []:
            name, _, rest = c.partition("=")
            value = rest.split(";", 1)[0]
            if value:
                self.cookies[name.strip()] = value
            else:
                self.cookies.pop(name.strip(), None)

    def call(self, method, path, body=None, ok=(200, 201, 204), form=False):
        status, payload = self._send(method, path, body, form)
        if status not in ok:
            raise RuntimeError(f"{method} {path} -> {status}: {payload}")
        return payload

    def try_call(self, method, path, body=None):
        status, payload = self._send(method, path, body)
        return status in (200, 201, 204), payload

    def login(self, email, password):
        self.call("GET", "/api/v1/csrf")
        self.call("POST", "/api/v1/auth/login", {"email": email, "password": password}, form=True)
        self.call("GET", "/api/v1/csrf")  # the token rotates on login
        me = self.call("GET", "/api/v1/me")
        return me


def items(payload):
    return payload["items"] if isinstance(payload, dict) and "items" in payload else (payload or [])


def by_name(payload, key="name"):
    return {x[key]: x for x in items(payload)}


def phone():
    return f"+91 9{rnd.randint(100000000, 999999999)}"


def person_pan():
    return "".join(rnd.choice(PAN_LETTERS) for _ in range(3)) + "P" + rnd.choice(PAN_LETTERS) \
        + f"{rnd.randint(1000, 9999)}" + rnd.choice(PAN_LETTERS)


def at(day, hh, mm=0):
    return dt.datetime.combine(day, dt.time(hh, mm), IST).isoformat()


def seed_temple(api):
    api.call("PUT", "/api/v1/temple", {
        "deity": "Shri Siddhivinayak and Devi Annapurna",
        "address": "14 Ganesh Peth, Near Shaniwar Wada, Pune, Maharashtra 411030",
        "helpline": "+91 20 2445 1008",
        "timings": "Abhishekam on Mondays at 6:00 am. Annadanam daily from 12:00 to 2:00 pm.",
        "announcement": "Diwali Lakshmi Puja on 8 November: register for a darshan pass online or at the counter.",
        "hours": {"morningOpen": "05:30", "morningClose": "12:30", "eveningOpen": "16:00", "eveningClose": "21:30"},
        "aartis": [
            {"name": "Kakad Aarti", "at": "05:45", "description": "The first aarti of the day"},
            {"name": "Madhyan Aarti", "at": "12:00", "description": "Noon aarti with naivedyam"},
            {"name": "Dhoop Aarti", "at": "18:30", "description": "Evening aarti at sunset"},
            {"name": "Shej Aarti", "at": "21:15", "description": "Night aarti before the doors close"},
        ],
        "calendar": "PURNIMANTA",
        "status": "OPEN",
        "statusNote": None,
    })
    api.call("PUT", "/api/v1/trust-profile", {
        "legalName": "Demo Seva Trust",
        "address": "14 Ganesh Peth, Pune, Maharashtra 411030",
        "pan": "AAATD4821K",
        "registration80g": "AAATD4821KF20214",
        "validFrom": "2021-04-01",
        "validTo": "2031-03-31",
    })
    print("temple profile, hours, aartis and 80G details set")


def seed_funds(api):
    have = by_name(api.call("GET", "/api/v1/donation-funds"))
    for name in ["Annadanam", "Gau Seva", "Temple Renovation", "Education Fund"]:
        if name not in have:
            have[name] = api.call("POST", "/api/v1/donation-funds", {"name": name, "active": True})
    print(f"funds: {', '.join(have)}")
    return [f["id"] for f in have.values()]


def seed_devotees(api, n, force):
    existing = api.call("GET", "/api/v1/devotees?page=0&size=1")
    if existing["total"] and not force:
        print(f"devotees: {existing['total']} already there, skipping (use --force to add more)")
        return [d["id"] for d in items(api.call("GET", "/api/v1/devotees?page=0&size=100"))]
    ids = []
    for i in range(n):
        first, last = rnd.choice(FIRST), rnd.choice(LAST)
        city, state, pin = rnd.choice(PLACES)
        d = api.call("POST", "/api/v1/devotees", {
            "fullName": f"{first} {last}",
            "phone": phone(),
            "email": f"{first}.{last}{i}@example.com".lower(),
            "addressLine": f"{rnd.randint(1, 220)}, {rnd.choice(STREETS)}",
            "city": city, "state": state, "pincode": f"{pin}{rnd.randint(10, 99)}",
            "dateOfBirth": (dt.date(1950, 1, 1) + dt.timedelta(days=rnd.randint(0, 20000))).isoformat(),
            "consentSource": rnd.choice(["IN_PERSON", "IN_PERSON", "ONLINE_FORM", "PHONE", "WRITTEN"]),
        })
        ids.append(d["id"])
    print(f"devotees: {len(ids)} added")
    return ids


def seed_donations(api, devotees, funds, n, force):
    if api.call("GET", "/api/v1/donations?page=0&size=1")["total"] and not force:
        print("donations: some already there, skipping")
        return
    fy_start = dt.date(TODAY.year if TODAY.month >= 4 else TODAY.year - 1, 4, 1)
    span = (TODAY - fy_start).days
    amounts = [101, 251, 501, 1001, 1101, 2100, 5001, 11000, 21000, 51000]
    weights = [14, 14, 16, 16, 10, 10, 8, 6, 4, 2]
    made = receipts = 0
    for _ in range(n):
        amount = rnd.choices(amounts, weights)[0]
        linked = rnd.random() < 0.7 and devotees
        mode = rnd.choices(["CASH", "UPI", "CHEQUE", "BANK_TRANSFER", "CARD"], [30, 40, 10, 15, 5])[0]
        d = api.call("POST", "/api/v1/donations", {
            "devoteeId": rnd.choice(devotees) if linked else None,
            "donorName": None if linked else f"{rnd.choice(FIRST)} {rnd.choice(LAST)}",
            "amount": f"{amount}.00",
            "mode": mode,
            "reference": None if mode == "CASH" else f"{mode[:3]}{rnd.randint(100000, 999999)}",
            "purpose": rnd.choice([None, "Abhishekam", "Annadanam on birthday", "Navratri seva", "In memory of parents"]),
            "fundId": rnd.choice(funds + [None, None]),
            "receivedOn": (fy_start + dt.timedelta(days=rnd.randint(0, span))).isoformat(),
        })
        made += 1
        # An 80G receipt for most of the bigger gifts, as a trust would issue them. Cash over
        # Rs 2,000 isn't deductible under 80G, and the app refuses to receipt it.
        eligible = mode != "CASH" or amount <= 2000
        if eligible and amount >= 1001 and rnd.random() < 0.6:
            city, state, pin = rnd.choice(PLACES)
            api.call("POST", f"/api/v1/donations/{d['id']}/receipt", {
                "donorPan": person_pan(),
                "donorAddress": f"{rnd.randint(1, 220)}, {rnd.choice(STREETS)}, {city} {pin}{rnd.randint(10, 99)}",
            })
            receipts += 1
    print(f"donations: {made} added, {receipts} with 80G receipts")


def seed_pujas(api):
    pujas = by_name(api.call("GET", "/api/v1/pujas"))
    wanted = [
        ("Rudrabhishekam", "Shiva", "Abhishekam with the Rudram chanted eleven times.", "1100.00"),
        ("Satyanarayan Puja", "Vishnu", "Family puja with katha and prasad, about two hours.", "751.00"),
        ("Ganesh Archana", "Ganesha", "108 names of Ganesha with durva and modak naivedyam.", "151.00"),
        ("Navagraha Shanti", "Navagraha", "Shanti puja for the nine planets with homam.", "2100.00"),
        ("Lakshmi Kubera Puja", "Lakshmi", "Puja for prosperity, popular before Diwali.", "501.00"),
        ("Archana", "Siddhivinayak", "Name and gotra read out at the next aarti.", "51.00"),
    ]
    for i, (name, deity, desc, dakshina) in enumerate(wanted):
        if name not in pujas:
            pujas[name] = api.call("POST", "/api/v1/pujas", {"name": name, "deity": deity, "description": desc,
                                                             "dakshina": dakshina, "active": True, "displayOrder": i})
    priests = by_name(api.call("GET", "/api/v1/priests"))
    for name, spec in [("Pandit Shrikant Joshi", "Rudrabhishekam, homam"),
                       ("Pandit Venkatesh Shastri", "Satyanarayan, Navagraha"),
                       ("Pandit Anand Kulkarni", "Archana, Ganesh puja")]:
        if name not in priests:
            priests[name] = api.call("POST", "/api/v1/priests", {"name": name, "phone": phone(),
                                                                 "specialties": spec, "active": True})
    print(f"pujas: {len(pujas)}, priests: {len(priests)}")
    return list(pujas.values()), list(priests.values())


def seed_bookings(api, pujas, priests, n, force):
    days = [TODAY + dt.timedelta(days=i) for i in range(15)]
    if not force and any(api.call("GET", f"/api/v1/puja-bookings?date={d.isoformat()}") for d in days):
        print("puja bookings: some already there, skipping")
        return
    active = [p for p in pujas if p["active"]]
    made = assigned = 0
    for _ in range(n):
        p = rnd.choice(active)
        b = api.call("POST", "/api/v1/puja-bookings", {
            "pujaId": p["id"],
            "devoteeName": f"{rnd.choice(FIRST)} {rnd.choice(LAST)}",
            "gotra": rnd.choice(GOTRAS), "nakshatra": rnd.choice(NAKSHATRAS), "rashi": rnd.choice(RASHIS),
            "familyNames": rnd.choice([None, "Wife and two children", "Parents", "Whole family"]),
            # Today first, so the counter's day view has something to show in a demo.
            "pujaDate": (TODAY if made < 4 else rnd.choice(days)).isoformat(),
            "phone": phone(), "email": None,
            "mode": rnd.choice(["CASH", "UPI", "CARD"]),
            "reference": None,
        })
        made += 1
        if priests and rnd.random() < 0.6:
            ok, _ = api.try_call("POST", f"/api/v1/puja-bookings/{b['id']}/priest",
                                 {"priestId": rnd.choice(priests)["id"]})
            assigned += ok
    print(f"puja bookings: {made} at the counter, {assigned} with a priest")


def seed_events(api):
    events = by_name(api.call("GET", "/api/v1/events"), key="title")
    year = TODAY.year
    wanted = [
        ("Diwali Lakshmi Puja", "Lakshmi puja at pradosh kaal, deepotsav in the courtyard and prasad for all.",
         dt.date(year, 11, 8), (18, 0), (22, 0), 800),
        ("Kartik Purnima Deep Daan", "Light a diya at the temple tank. Bring your own lamp or take one at the gate.",
         dt.date(year, 11, 24), (17, 30), (21, 0), 500),
        ("Gita Jayanti Path", "Recitation of all eighteen chapters of the Bhagavad Gita, open to everyone.",
         dt.date(year, 12, 20), (7, 0), (13, 0), 300),
        ("Annadanam Sunday", "Community lunch served by the Annadanam seva team.",
         TODAY + dt.timedelta(days=(6 - TODAY.weekday()) % 7 or 7), (12, 0), (14, 30), None),
    ]
    for title, desc, day, start, end, cap in wanted:
        if day <= TODAY or title in events:
            continue
        e = api.call("POST", "/api/v1/events", {"title": title, "description": desc,
                                                "startsAt": at(day, *start), "endsAt": at(day, *end),
                                                "capacity": cap, "registrationOpen": True})
        api.call("POST", f"/api/v1/events/{e['id']}/publish")
        if cap:
            for _ in range(rnd.randint(8, 20)):
                api.call("POST", f"/api/v1/events/{e['id']}/passes", {
                    "name": f"{rnd.choice(FIRST)} {rnd.choice(LAST)}", "count": rnd.randint(1, 5),
                    "phone": phone(), "email": None})
        events[title] = e
    print(f"events: {len(events)} published, with gate passes")


def seed_sevaks(api, force):
    teams = by_name(api.call("GET", "/api/v1/seva-teams"))
    wanted = [
        ("Annadanam", "Cooking and serving the daily community lunch.", 12, "meal",
         [("Morning prep", "08:00", "11:30"), ("Serving", "11:30", "14:30")]),
        ("Darshan queue", "Guiding devotees and keeping the queue moving on festival days.", 10, "queue",
         [("Morning", "05:30", "12:30"), ("Evening", "16:00", "21:30")]),
        ("Flowers and decoration", "Garlands, rangoli and decorating the mandap.", 6, "garland",
         [("Early", "05:00", "08:00")]),
        ("Gau Seva", "Feeding and caring for the cows at the gaushala.", 4, "cow",
         [("Morning", "06:00", "09:00"), ("Evening", "17:00", "19:00")]),
    ]
    for name, desc, target, icon, shifts in wanted:
        if name not in teams:
            teams[name] = api.call("POST", "/api/v1/seva-teams", {
                "name": name, "description": desc, "targetCount": target, "icon": icon,
                "shifts": [{"name": s, "startsAt": a, "endsAt": b} for s, a, b in shifts]})
    have = {s["fullName"] for s in items(api.call("GET", "/api/v1/sevaks"))}
    if have and not force:
        print(f"seva teams: {len(teams)}, sevaks: {len(have)} already there, skipping")
        return
    team_list = list(teams.values())
    added = 0
    for i in range(16):
        full = f"{rnd.choice(FIRST)} {rnd.choice(LAST)}"
        if full in have:
            continue
        team = rnd.choice(team_list)
        approved = i < 12  # a few left pending so the approval queue has something in it
        api.call("POST", "/api/v1/sevaks", {
            "fullName": full, "phone": phone(), "email": f"sevak{i}@example.com",
            "sevaAreas": team["name"], "availability": rnd.choice(["Weekends", "Weekday mornings", "Festivals only"]),
            "notes": None, "approved": approved,
            "teamId": team["id"] if approved else None,
            "duty": rnd.choice([None, "Team lead", "Counter", "Kitchen"]) if approved else None})
        have.add(full)
        added += 1
    print(f"seva teams: {len(teams)}, sevaks: {added} added")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", required=True, help="staff site of the trust, e.g. https://demo.stg-seva.example.in")
    ap.add_argument("--host", help="Host header override, for reaching the app through a tunnel")
    ap.add_argument("--devotees", type=int, default=45)
    ap.add_argument("--donations", type=int, default=90)
    ap.add_argument("--bookings", type=int, default=14)
    ap.add_argument("--force", action="store_true", help="add devotees and donations even if some exist")
    args = ap.parse_args()

    email, password = os.environ.get("SEED_EMAIL"), os.environ.get("SEED_PASSWORD")
    if not email or not password:
        sys.exit("set SEED_EMAIL and SEED_PASSWORD (a TRUST_ADMIN of the trust to fill)")

    api = Api(args.base, args.host)
    api.login(email, password)
    print(f"logged in as {email}")

    seed_temple(api)
    funds = seed_funds(api)
    devotees = seed_devotees(api, args.devotees, args.force)
    seed_donations(api, devotees, funds, args.donations, args.force)
    pujas, priests = seed_pujas(api)
    seed_bookings(api, pujas, priests, args.bookings, args.force)
    seed_events(api)
    seed_sevaks(api, args.force)
    print("done")


if __name__ == "__main__":
    main()
