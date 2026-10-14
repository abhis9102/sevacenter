# 0033 — Terraform state backend and CI roles: plan reads, apply builds behind an approval

- **Status:** Accepted (2026-10-14)
- **Deciders:** AppSec (Abhi)
- **Relates to:** ADR 0003 (GitHub Actions + OIDC), ADR 0032 (network), CR-1, CR-2, CR-4, CR-5, CR-6

## Context

Before CI can run Terraform it needs somewhere to keep state and a way into AWS. Both are the
most valuable targets in the pipeline:
- **State** lists every resource and can hold sensitive values. Whoever can rewrite it steers the
  next apply.
- **A CI role that can create IAM roles** can create an administrator.
- **`terraform plan` runs the pull request's code.** Providers and data sources execute with the
  plan role's permissions, so a malicious branch acts as that role.

## Decision

**A bootstrap stack (`infra/bootstrap/`), applied by a human only.** An administrator signed in
through Identity Center (CR-1) applies it; CI never does. The CI roles therefore can't change
their own permissions. It starts on local state and then moves into the bucket it created (steps
in `main.tf`).

**State bucket:**
- S3 in ap-south-1 with versioning, keeping the last 10 versions plus 90 days.
- Its own customer-managed key (CR-4), public access blocked, TLS only, ACLs off.
- S3-native locking (no DynamoDB table) and `prevent_destroy`.
- Stacks don't set `encrypt = true` in the backend. Without a key ID that setting writes SSE-S3,
  which overrides the bucket's KMS default. The bucket policy also refuses SSE-S3 writes and writes
  under any other KMS key.

**GitHub OIDC (CR-6):** the account's single provider for GitHub is imported, not duplicated, and
protected from destroy.

**Two roles, one hour each:**

| | `sevacenter-tf-plan` | `sevacenter-tf-apply` |
|---|---|---|
| Trust (`sub`, exact) | `repo:abhis9102/sevacenter:pull_request` | `repo:abhis9102/sevacenter:environment:staging` |
| Reads | AWS `ReadOnlyAccess`, minus data reads (S3 objects outside state, secret and parameter values, log events, image layers, decryption outside the state key) | `ReadOnlyAccess` (refresh) |
| Writes | lock files only (`*.tflock`), so it can't rewrite state | state; the services the stacks use; IAM only under `/sevacenter/workload/` |
| Both | deny every regional action outside ap-south-1 (CR-2) | |

**Environment protection does the "main only, human approved" part.** AWS matches only the
token's `sub`, not its branch. The GitHub environment `staging` allows deployments from `main` only
and requires AppSec's approval of each run.

**Permissions boundary.** Every role that apply creates must carry the boundary
`sevacenter-workload-boundary` (the condition on `iam:CreateRole`). The boundary allows:
- the Session Manager agent;
- image pulls, log and metric writes;
- use of our keys, secrets and parameters;
- project bucket objects;
- the night stop/start (ADR 0032).

Removing a boundary is denied. The boundary and the CI roles live outside the workload path, so
apply can't edit them.

**G7 custom check CKV_SEVA_3.** Every GitHub OIDC trust must pin `sub` exactly. The built-in
CKV_AWS_358 missed `repo:*` when the provider is referenced by `.arn` (see
`docs/security/iac-scan.md`).

## Consequences

- **CI can't reach AWS until this is applied**, and applying it is a manual, privileged step. That
  separation is the point: a change to it means a PR plus a human apply.
- **The apply role has service-wide actions** (`ec2:*`, `rds:*`, and others) where resources have
  generated IDs. They're recorded in `.checkov/accepted.toml` with their limits. A tighter,
  per-action policy can come from the access actually used (IAM Access Analyzer policy
  generation) once the stacks are stable.
- **Plan can still see configuration** (resource attributes, policies) of the whole account. That
  is what a plan needs; personal data isn't in configuration.
- **The pre-existing hand-made deploy role** for the old staging box trusts the same provider. It
  isn't in Terraform and isn't scanned. It goes away with the old box once staging runs in
  ap-south-1.
