# SBOM and build attestation policy

Gate **G4**. An SBOM (software bill of materials) is the exact list of what we ship: every
component, version, hash and license. It answers "are we affected?" in seconds when the next
Log4Shell lands, and it's what enterprise customers, auditors and regulators increasingly ask for.

## How it's produced

| Piece | What | Where |
|---|---|---|
| `cyclonedx-maven-plugin` (pre-configured by the Boot parent) | CycloneDX 1.6 JSON generated **from Maven's own resolution** during the build | `backend/pom.xml`; embedded in the jar at `META-INF/sbom/application.cdx.json` |
| `build` job | Uploads the jar + its SBOM as one artifact, so later jobs check and sign exactly what was built | CI artifact `backend-build` (14 days) |
| `sbom` job (required check) | Validate → scan shipped components → severity gate → drift | every PR, push, daily |
| `attest` job (`main` only) | Signs **SLSA build provenance** and the **SBOM** for the jar's digest | GitHub attestations + public Sigstore/Rekor log |

Runtime scope only: test libraries (JUnit, Testcontainers, …) are not shipped, so they're not in the
SBOM. That's why the SBOM lists ~113 components where the G3 pom scan sees ~167.

## Checks (`tools/security/sbom_check.py`)

- **validate**: CycloneDX ≥ 1.4, subject is `pkg:maven/app.sevacenter/backend@…`, components present,
  every component has a purl and version, nothing with scope `excluded`. An SBOM that can't be
  matched against advisories is decoration.
- **drift**: every component in the SBOM was also seen, at the same version, by the G3 scan (which
  resolves the tree itself). A mismatch is a G3 blind spot. Compared by full `group:artifact`,
  because artifactIds collide (`org.postgresql:postgresql` vs `org.testcontainers:postgresql`).
- **severity gate**: the same CVSS ≥ 7.0 rule as G3, applied to what we actually ship.

## Attestations: proving where a jar came from

On every push to `main`, the `attest` job (which never builds or runs tests; it only signs) creates two
attestations for the jar's SHA-256:
1. **Build provenance** (SLSA): which repo, workflow, commit and runner produced it.
2. **SBOM**: this exact component list belongs to this exact jar.

Signing is **keyless**: the job's OIDC token is exchanged for a short-lived Sigstore certificate, so
there's no signing key to leak or rotate. Anyone can verify:

```bash
gh run download <run-id> -R abhis9102/sevacenter -n backend-build -D build
gh attestation verify build/backend-*.jar -R abhis9102/sevacenter
gh attestation verify build/backend-*.jar -R abhis9102/sevacenter \
  --predicate-type https://cyclonedx.org/bom
```

A jar modified by even one byte has a different digest, and verification fails.

## Exposure

- `/actuator/sbom` stays **unexposed** (`management.endpoints.web.exposure.include: health,info`).
  Exact versions of every library are an attacker's shopping list; the SBOM goes to people we choose.
- Public repo → attestation entries are in the public Rekor transparency log (repo, workflow, commit).
  That's expected for a public repo and is what makes verification possible for anyone.

## Not yet

- Reproducible builds (bit-for-bit identical jars across rebuilds).
- Publishing the SBOM with releases and container images (M5/M6), and VEX statements for
  "affected but not exploitable".
- License policy (osv-scanner `--licenses` allowlist): all 113 components carry license data already.
