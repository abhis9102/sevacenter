# 0032 — Network: three private-by-default tiers in one AZ, an edge box instead of NAT gateway and ALB

- **Status:** Accepted (2026-10-14)
- **Deciders:** AppSec (Abhi)
- **Relates to:** `docs/security/cloud-security-requirements.md` (CR-2, CR-3, CR-4), ADR 0003

## Context

M6 puts staging on AWS in ap-south-1. Staging runs on a fixed credit budget, so the managed
networking pieces are the biggest line items. Rough Mumbai prices per month: NAT gateway $35–45,
ALB about $18 plus its public addresses, five or six interface endpoints $40–50. Together they cost
more than the compute and database combined. The controls themselves (security groups, DNS
Firewall, flow logs, a customer-managed key) cost cents to about a dollar.

## Decision

**Layout.** VPC `172.16.0.0/16`. Workloads run in one AZ (`ap-south-1a`); `ap-south-1b` holds
subnets only, because an RDS subnet group (and a future load balancer) needs two AZs.

| Tier | Subnets (1a, 1b) | Route out | Holds |
|---|---|---|---|
| public | `172.16.0.0/24`, `172.16.1.0/24` | internet gateway | edge box |
| app | `172.16.10.0/24`, `172.16.11.0/24` | edge box (NAT), S3 gateway endpoint | app box |
| db | `172.16.20.0/24`, `172.16.21.0/24` | none (VPC only) | RDS |

No subnet assigns public IPs automatically, and the default security group has no rules.

**Edge box instead of NAT gateway + ALB.** One small instance in the public subnet terminates TLS
(Caddy), redirects 80 to 443, sends HSTS and proxies to the frontend on the app box. It also NATs
the app tier's outbound HTTPS. About $7 a month, where the managed pair costs about $60.

**Egress control, cheapest first.**
- Security groups: the app box sends only 443 out and 5432 to the database group. The database
  group allows 5432 in from the app group only, and nothing out. Only the edge box takes traffic
  from the internet, on 80/443.
- **DNS Firewall:** the VPC resolver answers only allowlisted names (AWS service endpoints,
  Let's Encrypt, Razorpay, plus `extra_allowed_domains`); everything else gets NODATA, and it fails
  closed.
- S3 gateway endpoint (free) for image layers, limited by policy to this account's buckets and
  ECR's layer bucket.
- No interface endpoints yet: AWS API calls go through the edge box over TLS.

**Port 80** stays open on the edge box only, to redirect (CR-4). HSTS covers later visits; the
production `.app` domains are on the browsers' HSTS preload list.

**Flow logs:** all traffic, 1-minute aggregation, v5 fields, Parquet in S3 (ap-south-1),
encrypted with the environment's KMS key, kept 180 days (CERT-In), then deleted.

**One KMS key per environment**, rotated yearly, used by every service that stores data.

**Budget alerts** at 25/50/100% of the monthly limit, plus a forecast alert.

## Consequences

- **Gained:** the private app and database tiers of the full design at about a sixth of the
  network cost. Moving to a NAT gateway and ALB later is a route and DNS change, not a redesign.
- **Accepted:**
  - The edge box is ours to patch.
  - It's a single point of failure for staging.
  - It's the most valuable box to an attacker (it sees decrypted traffic and all egress).
  - Mitigations: no SSH (SSM Session Manager only), IMDSv2, minimal packages, its own security
    group.
- **DNS Firewall doesn't stop a connection to a hard-coded IP address.** That needs a filtering
  proxy or AWS Network Firewall (about $280+ per month), deferred until production.
- **One AZ for workloads:** an AZ outage takes staging down. Production revisits this.
- **G7 findings accepted with expiry** (`.checkov/accepted.toml`):
  - The KMS key policy's root statement.
  - Port 80 on the edge box.
  - Cross-region replication, which CR-2 forbids.
  - Event notifications and access logging on the flow-log bucket.
  - Security groups not yet attached, until the compute round, with a 30-day expiry.
