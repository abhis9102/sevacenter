# 0034 — Staging compute and database: two boxes, RDS, images only from our registry

- **Status:** Accepted (2026-10-14)
- **Deciders:** AppSec (Abhi); rollout delegated to Claude
- **Relates to:** ADR 0032 (network), ADR 0033 (state and CI roles), CR-2…CR-6

## Context

Staging first ran on a hand-built box in ap-southeast-2 (Sydney), outside CR-2, with a deploy
role that could run any shell script on it. ADR 0032 sized a lean, private design for the credit
budget. This ADR fills it in and moves staging to ap-south-1.

## Decision

**Edge box** (t3.micro, public subnet, Elastic IP; free-plan accounts can launch only free-tier-eligible types):
- Caddy terminates TLS (on-demand Let's Encrypt certificates, only for our two base domains), redirects 80 to 443, sends HSTS and proxies to the app box.
- iptables NAT for the app tier: only TCP 443 from the app subnets is forwarded.

**App box** (t3.micro, private subnet, fixed IP `172.16.10.10`):
- Backend, frontend and Mailpit as containers. Read-only root filesystems, no capabilities, `no-new-privileges`, memory limits.
- Only the frontend's port is published, on the private address, and only the edge security group reaches it.
- Docker is used without Compose (not packaged for Amazon Linux 2023); `/opt/sevacenter/start.sh` runs at boot and on redeploy.

**Both boxes:**
- No SSH key: access is through Session Manager.
- IMDSv2 only, with hop limit 1, so containers can't reach the instance credentials.
- Root volumes encrypted with the environment key.
- Roles live under `/sevacenter/workload/` with the permissions boundary.
- The edge role is explicitly denied the app's secrets. The managed SSM policy alone would allow it.

**Database:** RDS PostgreSQL 16, db.t4g.micro, private, encrypted with the environment key.
- `rds.force_ssl = 1`, and the JDBC URL uses `sslmode=verify-full` against the RDS CA bundle (CR-4: Sydney's connection was encrypted but never verified).
- The master password is generated and held by RDS in Secrets Manager, never in Terraform state. Flyway migrates as that owner; the app runs as `sevacenter_app` under forced RLS.
- 1-day backups (the free plan's maximum; 7 days after the upgrade), deletion protection, slow-query log to an encrypted log group kept 180 days.

**Images only from our ECR:**
- Repositories are immutable-tag, scan-on-push and KMS-encrypted.
- Caddy and Mailpit are mirrored in by digest by the deploy workflow, so no public registry is on the DNS allowlist.

**Deploys:** the deploy role pushes images, pins digests in `/sevacenter/staging/images/*`, and may run only the `sevacenter-staging-redeploy` document, only on the box tagged `sevacenter-role=app`.

**Secrets:** read at run time from SecureString parameters (`/sevacenter/staging/app/*`, environment key) and Secrets Manager into a root-only file on tmpfs. They're created outside Terraform, so their values never reach state.

**Night schedule:**
- The boxes stop at 23:00 IST and start at 08:00 IST.
- The database starts 20 minutes before the boxes and stops 10 minutes after them.

## Consequences

- Staging costs about $25 a month with the night stop, inside the credit (ADR 0032).
- `TRUSTED_PROXIES=.*` on the backend stays acceptable only because the backend is unreachable
  except from the frontend container. That network isolation is load-bearing.
- A new AMI isn't picked up automatically (`ignore_changes = [ami]`). Patching means replacing an
  instance deliberately, until patching is automated.
- The data moves from Sydney to Mumbai once (dump and restore through Session Manager tunnels); Sydney is
  then deleted without a final snapshot, so no copy of personal data stays outside India (CR-2).
