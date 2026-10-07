# Cloud Security Requirements (M6)

Set by AppSec (Abhi) on 2026-10-06, **before** any cloud design exists, so the design has to meet
them rather than have them patched on afterwards. Every architecture, Terraform change and
deploy-pipeline change in M6+ is reviewed against this list, and each requirement says how we
*know* it holds: an automated check where one is possible, manual review where it isn't.

Scope: every AWS account and resource SevaCenter runs, starting with staging.

| ID | Requirement |
|---|---|
| CR-1 | No long-lived credentials |
| CR-2 | Data stays in India |
| CR-3 | The database is private |
| CR-4 | Personal data is encrypted |
| CR-5 | Least-privilege IAM |
| CR-6 | IAM roles and STS wherever possible |

## CR-1 — No long-lived credentials

A credential that never expires is valid wherever it leaks to (a laptop backup, a CI log, a
screenshot), until a human notices and revokes it.

- **Humans** sign in through **IAM Identity Center** with MFA and receive short-lived session
  credentials (`aws sso login`). No IAM users with access keys or console passwords.
- **CI** authenticates with **GitHub OIDC**: the workflow exchanges its signed identity token for
  STS credentials that expire within the hour (ADR 0003). The role's trust policy names this
  repository and the exact branch or environment allowed to assume it.
- **Workloads** (ECS tasks) use their **task role**. Credentials are delivered by the platform and
  rotated automatically, never passed in environment variables.
- **Root user:** MFA on, no access keys, used only for the few tasks that need root.

**Verified by:** Checkov in CI fails on IAM users, access keys and login profiles in Terraform; no
AWS key in any GitHub secret (repo and environment secrets reviewed); IAM credential report shows
no active access keys before every staging apply.

**Gate before the first `terraform apply`:** the account's IAM credential report shows no active
access keys and no IAM users other than documented break-glass, and human access works through
Identity Center.

## CR-2 — Data stays in India

Donor PAN, devotee contact details and donation records are personal data of Indian residents.
Keeping all of it in one Indian region gives one simple answer to DPDP and customer questions.

- Every resource that stores or processes personal data is in **ap-south-1 (Mumbai)**: database,
  backups and snapshots, secrets, encryption keys, logs, container tasks, object storage.
- No cross-region replication or snapshot/backup copies outside ap-south-1.
- Global AWS services are used only where they hold no personal data: IAM, Route 53 (DNS records).
  No CDN or edge cache in front of responses that carry personal data.

**Verified by:** the Terraform provider region is a validated variable restricted to `ap-south-1`;
roles that CI assumes deny every action outside ap-south-1 (`aws:RequestedRegion`, with the
documented global-service exceptions); manual review rejects cross-region replication,
snapshot copies and CDNs.

## CR-3 — The database is private

- RDS runs in **private subnets** with no route to an internet gateway, and
  `publicly_accessible = false`.
- Its security group allows **only** the application's and the migration task's security groups,
  on 5432. No CIDR ranges, no `0.0.0.0/0`.
- Human access, when it is ever needed, goes through **SSM Session Manager** port forwarding:
  audited, IAM-authorised, no bastion with a public IP, no SSH keys.

**Verified by:** Checkov (RDS not publicly accessible, no open ingress in security groups); manual
review of every security-group rule.

## CR-4 — Personal data is encrypted

**At rest**, with a **customer-managed KMS key** (rotation on) so access to the key is an IAM
decision we control and can audit:
- RDS storage, automated backups and snapshots.
- Secrets Manager secrets.
- S3 buckets, including Terraform state (state can contain sensitive values); all S3 public access
  blocked.
- CloudWatch log groups.

**In transit:**
- Internet → load balancer: HTTPS only (TLS 1.2+), HTTP redirects to HTTPS.
- Application → database: TLS required by the server (`rds.force_ssl = 1`) and the certificate
  verified by the client.

**In the application:** the donor PAN stays encrypted *by the application* (ADR 0012), so neither
a database dump nor a database administrator sees it. In M6 its key moves from an environment
variable to KMS (envelope encryption).

**Never in Terraform state:** the database master password is generated and stored by RDS in
Secrets Manager (`manage_master_user_password`). Terraform never sees it.

**Verified by:** Checkov (encryption and KMS rotation checks for RDS, S3, Secrets Manager, logs;
load balancer listener protocols); manual review of the database parameter group and the
application's JDBC TLS settings.

## CR-5 — Least-privilege IAM

- **One role per purpose:** separate roles for Terraform plan (read-only), Terraform apply, image
  push, service deploy, each task's execution role, each task's runtime role, and the migration
  task.
- **No wildcard actions on wildcard resources.** Actions are listed, and resources are scoped to
  ARNs; any unavoidable `*` (some AWS APIs have no resource-level permissions) is commented with
  the reason.
- **Conditions narrow further** where AWS supports them: the OIDC subject (repo + ref/environment),
  region, source account, tags.
- Only the application's task role can decrypt with the application's KMS key; only the migration
  task can read the database owner's secret (ADR 0031: the running app never holds the owner
  password).

**Verified by:** Checkov IAM checks (wildcard actions/resources, privilege escalation, broad
`iam:PassRole`); manual review of every policy document as a sensitive path; IAM Access Analyzer
for external access.

## CR-6 — IAM roles and STS wherever possible

Prefer temporary credentials from STS over any stored secret, everywhere AWS supports it:
- Humans → Identity Center permission sets (roles), not users.
- CI → OIDC → `AssumeRoleWithWebIdentity`.
- Containers → task roles; AWS service-to-service access through roles and resource policies.
- A stored secret is acceptable only for things that can't use IAM (the database's own login
  passwords, third-party API keys such as Razorpay), and then it lives in Secrets Manager, is
  encrypted under CR-4, and is readable only by the role that needs it.

**Verified by:** manual review: every stored secret in the design is listed with the reason IAM
can't replace it.

## How this list is used

- The architecture (next ADR) states, for each CR, which design choice meets it.
- G7 (Checkov) runs on every PR that touches Terraform and blocks on failed checks; any exception
  is recorded with a reason and an expiry, like the ZAP and Trivy exceptions.
- A requirement changes only through a PR to this file, approved by AppSec.
