#!/usr/bin/env python3
"""SBOM checks (G4). Policy: docs/security/sbom.md

  validate  The CycloneDX SBOM is well-formed and useful: the right subject, components present,
            and every component has a purl + version (an SBOM you can't match against advisories
            is decoration).
  drift     Every component Maven actually ships (the SBOM) was also seen, at the same version,
            by the G3 scan, which resolves the dependency tree itself. A mismatch means G3 has a
            blind spot. Compared by full purl (group:artifact): artifactIds alone collide, e.g.
            org.postgresql:postgresql vs org.testcontainers:postgresql.

Stdlib only.
"""

from __future__ import annotations

import argparse
import json
import sys
from urllib.parse import unquote

MIN_SPEC = (1, 4)


def maven_coords(purl: str) -> tuple[str, str] | None:
    """pkg:maven/<group>/<artifact>@<version>?type=jar -> ("group:artifact", "version")."""
    if not purl.startswith("pkg:maven/"):
        return None
    path = purl[len("pkg:maven/"):].split("?", 1)[0].split("#", 1)[0]
    name, _, version = path.partition("@")
    group, _, artifact = name.rpartition("/")
    return f"{unquote(group)}:{unquote(artifact)}", unquote(version)


def load(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def validate(sbom_path: str, subject: str) -> list[str]:
    bom, errors = load(sbom_path), []
    if bom.get("bomFormat") != "CycloneDX":
        errors.append(f"bomFormat is {bom.get('bomFormat')!r}, expected 'CycloneDX'")
    spec = tuple(int(x) for x in str(bom.get("specVersion", "0.0")).split(".")[:2])
    if spec < MIN_SPEC:
        errors.append(f"specVersion {bom.get('specVersion')} < {'.'.join(map(str, MIN_SPEC))}")
    meta_purl = bom.get("metadata", {}).get("component", {}).get("purl", "")
    if not meta_purl.startswith(subject):
        errors.append(f"subject is {meta_purl!r}, expected it to start with {subject!r}")
    comps = bom.get("components") or []
    if not comps:
        errors.append("no components: an empty SBOM claims we ship nothing")
    for c in comps:
        ref = c.get("purl") or c.get("bom-ref") or c.get("name", "?")
        if not c.get("purl"):
            errors.append(f"{ref}: no purl, can't be matched against advisories")
        if not c.get("version"):
            errors.append(f"{ref}: no version")
        if c.get("scope") == "excluded":
            errors.append(f"{ref}: scope 'excluded' (test/provided) must not be in a shipping SBOM")
    print(f"{sbom_path}: CycloneDX {bom.get('specVersion')}, subject {meta_purl}, "
          f"{len(comps)} components, {sum(1 for c in comps if c.get('hashes'))} with hashes, "
          f"{sum(1 for c in comps if c.get('licenses'))} with licenses")
    return errors


def drift(sbom_path: str, osv_json: str) -> list[str]:
    shipped = {}
    for c in load(sbom_path).get("components") or []:
        coords = maven_coords(c.get("purl", ""))
        if coords:
            shipped.setdefault(coords[0], set()).add(coords[1])
    seen = {}
    for r in load(osv_json).get("results", []):
        for p in r["packages"]:
            if p["package"].get("version"):
                seen.setdefault(p["package"]["name"], set()).add(p["package"]["version"])
    errors = []
    for name, versions in sorted(shipped.items()):
        if name not in seen:
            errors.append(f"{name}@{','.join(sorted(versions))} ships but the G3 scan never saw it")
        elif not versions & seen[name]:
            errors.append(f"{name}: ships {sorted(versions)}, G3 scanned {sorted(seen[name])}")
    print(f"drift: {len(shipped)} shipped components vs {len(seen)} scanned by G3, "
          f"{len(errors)} mismatch(es)")
    return errors


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    v = sub.add_parser("validate", help="SBOM is well-formed, complete and about the right subject")
    v.add_argument("sbom")
    v.add_argument("--subject", default="pkg:maven/app.sevacenter/backend@")
    d = sub.add_parser("drift", help="every shipped component was seen by the G3 scan")
    d.add_argument("sbom")
    d.add_argument("osv_json", help="osv-scanner --all-packages JSON of the pom scan")
    args = p.parse_args()

    errors = validate(args.sbom, args.subject) if args.cmd == "validate" else drift(args.sbom, args.osv_json)
    for e in errors:
        print(f"::error::{e}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
