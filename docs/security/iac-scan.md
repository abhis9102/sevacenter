# IaC scan policy

Gate **G7**. From M6 the infrastructure is code (Terraform under `infra/`), so a public database
or an `iam:*` policy is a line in a PR before it's a resource in AWS. This gate reads that code and
blocks the merge, before `terraform apply` can create it. It's the pre-deployment half of cloud
security; checking the live account for drift (CSPM, Prowler) is the other half, later in M6.

## Why, when, where

| | |
|---|---|
| **Risk covered** | Insecure infrastructure written in Terraform: public data stores, open security groups, missing encryption, over-broad IAM, long-lived credentials (CR-1 to CR-5, `cloud-security-requirements.md`) |
| **Risk not covered** | Changes made outside Terraform (console clicks), drift, and what's actually live: CSPM. Values only known at plan time (variables, module outputs): scanning the plan file, round 2. Dockerfiles: G6. |
| **When** | Every PR, push to `main` and the daily run |
| **Where** | Job `iac` in `.github/workflows/ci.yml`, check **IaC scan (Checkov)**. Same script locally: `make iac-scan` |
| **Tool** | Checkov 3.3.26, open-source, digest-pinned image, run with no API key (`--skip-download`): nothing leaves the runner |

**Why Checkov, when Trivy can also scan Terraform:** one tool per asset, so a finding has one gate
and one ignore file. Checkov owns Terraform because of its graph checks (rules across resources,
e.g. a bucket and its separate public-access-block resource), plan-file scanning and Python custom
checks. Trivy keeps Dockerfiles (`--misconfig-scanners dockerfile`, G6).

## How it runs (`tools/security/iac_scan.sh`)

1. **Accepted risks** (`.checkov/accepted.toml`) are validated. An invalid entry fails the job.
2. **Self-test.** Checkov scans `.checkov/fixtures/insecure`, Terraform that breaks the cloud
   requirements on purpose, and the result must match `.checkov/fixtures/expected.txt` line by line
   (20 expectations: what must fail and what must pass). A check that stops loading, or a Checkov
   upgrade that renames one, fails here instead of looking like clean infrastructure.
3. **Scan `infra/`** (no Terraform yet: the step says so and passes). Checkov runs with
   `--soft-fail`; `tools/security/iac_policy.py gate` decides, the same split as ZAP and
   `dast_policy.py`. SARIF of `infra/` (never the fixture) goes to code scanning.

## What blocks

**Every failed check that isn't a valid accepted risk.** Open-source Checkov reports no severities
(they come from the paid platform), so there's no "High and above" line like G3 or G6 draw. That's
strict on purpose while the infrastructure is new and small: each finding is either fixed or
becomes a written, expiring decision. Also blocking:

- **Parsing errors.** A file Checkov couldn't read wasn't scanned.
- **Coverage.** `.tf` files present but 0 resources seen means nothing was scanned.
- **Inline `#checkov:skip=...`.** Inline skips never expire and hide inside feature diffs, so
  acceptances live in `.checkov/accepted.toml`: one check ID, the resource address(es), a reason of
  20+ characters, an `ignoreUntil` at most 90 days out (same rules as G3, G5, G6). An acceptance
  that no longer matches anything is reported as a warning so it gets cleaned up.

**Expect noise, and tune it in the open.** One `aws_db_instance` fails 10 checks; only one of them
(publicly accessible) is a real exposure. Others are cost or operations choices (Multi-AZ,
Performance Insights, enhanced monitoring), and some contradict our requirements: cross-region S3
replication (CKV_AWS_144) would copy personal data out of the region (CR-2). Each gets decided when
real resources exist: fixed, or accepted with the reason written down.

## Custom checks (`.checkov/checks/`)

Built-ins don't cover two of our requirements, found by writing the insecure fixture first:

| ID | Rule | Why the built-ins miss it |
|---|---|---|
| `CKV_SEVA_1` | Ingress from `0.0.0.0/0` or `::/0` only on TCP 80/443 | Built-ins check single ports (22, 3389) or all ports; **Postgres 5432 open to the internet passed**. Covers inline `ingress`, `aws_security_group_rule` and `aws_vpc_security_group_ingress_rule` |
| `CKV_SEVA_2` | No `aws_iam_access_key` | CKV_AWS_273 flags the IAM *user*; nothing flags the long-lived key itself (CR-1), whose secret Terraform also writes into state |

The folder needs an `__init__.py`. Without it Checkov logs "Cannot load any check here" at INFO and
carries on: the custom checks silently never run, and the scan looks clean. The self-test caught
exactly that.

## Validation (2026-10-07)

| Case | Result |
|---|---|
| Self-test, 20 expectations across CR-1, CR-3, CR-4, CR-5 | **Pass** |
| Custom checks without `__init__.py` | Not loaded, 0 results, no error from Checkov. Fixed; now covered by the self-test |
| `--quiet` on the Checkov run | Drops *passed* checks from the JSON: the self-test's "must pass" lines failed. Removed |
| Temporary `infra/` with a public RDS instance | **Blocked**: 10 failures, annotated by file and line |
| Same, plus a valid acceptance (CKV_AWS_157) | That one accepted; the rest still block |
| Inline `#checkov:skip=CKV_AWS_17:it is fine trust me` | **Blocked**: inline skip rejected |
| Acceptance with a 5-character reason and a 2027 expiry | **Blocked** at step 1, both problems named |
| `trivy config .` before G6 was limited to Dockerfiles | Read the fixture: 21 Terraform failures in G6. Now 0 |

## Known gaps

- Static HCL only: values from variables and modules aren't resolved. Round 2 adds scanning of
  `terraform plan` output, where they are.
- No CSPM yet: what's actually deployed (and anything changed by hand) is checked by Prowler later in M6.
