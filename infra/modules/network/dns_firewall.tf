# DNS Firewall (ADR 0032): the VPC resolver answers only names on the allowlist; everything else
# gets NODATA. Security groups allow 443 to any address; this decides which *names* resolve, so
# code that's been tampered with can't look up an attacker's server. It doesn't stop a connection
# to a hard-coded IP address: that needs a filtering proxy or Network Firewall (not at this budget).

locals {
  allowed_domains = concat([
    "*.amazonaws.com.",              # ECR, S3, SSM, Secrets Manager, KMS, CloudWatch, STS, RDS endpoints
    "*.internal.",                   # VPC-internal host names
    "acme-v02.api.letsencrypt.org.", # edge box certificates
    "api.razorpay.com.",             # online donations (ADR 0013)
  ], [for d in var.extra_allowed_domains : endswith(d, ".") ? d : "${d}."])
}

resource "aws_route53_resolver_firewall_domain_list" "allowed" {
  name    = "${var.name}-allowed"
  domains = local.allowed_domains
}

resource "aws_route53_resolver_firewall_domain_list" "everything" {
  name    = "${var.name}-everything"
  domains = ["*."]
}

resource "aws_route53_resolver_firewall_rule_group" "this" {
  name = var.name
}

resource "aws_route53_resolver_firewall_rule" "allow" {
  name                    = "allow-listed"
  action                  = "ALLOW"
  firewall_domain_list_id = aws_route53_resolver_firewall_domain_list.allowed.id
  firewall_rule_group_id  = aws_route53_resolver_firewall_rule_group.this.id
  priority                = 100
}

resource "aws_route53_resolver_firewall_rule" "block_rest" {
  name                    = "block-everything-else"
  action                  = "BLOCK"
  block_response          = "NODATA"
  firewall_domain_list_id = aws_route53_resolver_firewall_domain_list.everything.id
  firewall_rule_group_id  = aws_route53_resolver_firewall_rule_group.this.id
  priority                = 200
}

resource "aws_route53_resolver_firewall_rule_group_association" "this" {
  name                   = var.name
  firewall_rule_group_id = aws_route53_resolver_firewall_rule_group.this.id
  vpc_id                 = aws_vpc.this.id
  priority               = 101
}

# Fail closed: if DNS Firewall itself is unavailable, queries are blocked rather than let through.
resource "aws_route53_resolver_firewall_config" "this" {
  resource_id        = aws_vpc.this.id
  firewall_fail_open = "DISABLED"
}
