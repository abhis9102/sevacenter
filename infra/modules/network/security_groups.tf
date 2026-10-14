# Security groups (ADR 0032). Every rule names its peer group; the only CIDR is the internet on
# 80/443 into the edge box (CKV_SEVA_1). Rules are separate resources, so each one is reviewable on
# its own line of a diff.
#
#   internet --80/443--> edge --3000--> app --5432--> db
#   app --443--> edge (forwarded out by NAT)      edge --443--> internet

resource "aws_security_group" "edge" {
  name        = "${var.name}-edge"
  description = "Edge box: public TLS entry point and NAT for the app tier"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${var.name}-edge" }
}

resource "aws_security_group" "app" {
  name        = "${var.name}-app"
  description = "App box: reachable only from the edge box"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${var.name}-app" }
}

resource "aws_security_group" "db" {
  name        = "${var.name}-db"
  description = "Database: reachable only from the app tier"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${var.name}-db" }
}

# --- edge ------------------------------------------------------------------------------------

# Port 80 only redirects to HTTPS (and answers ACME HTTP-01 challenges); CR-4.
resource "aws_vpc_security_group_ingress_rule" "edge_http" {
  security_group_id = aws_security_group.edge.id
  description       = "HTTP from the internet: redirect to HTTPS only"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "edge_https" {
  security_group_id = aws_security_group.edge.id
  description       = "HTTPS from the internet"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

# NAT: the app tier's outbound HTTPS arrives here to be forwarded.
resource "aws_vpc_security_group_ingress_rule" "edge_nat_from_app" {
  security_group_id            = aws_security_group.edge.id
  description                  = "Outbound HTTPS from the app tier, to be NATed"
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}

resource "aws_vpc_security_group_egress_rule" "edge_https_out" {
  security_group_id = aws_security_group.edge.id
  description       = "HTTPS out: NATed app traffic, OS updates, Let's Encrypt, SSM"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "edge_to_app" {
  security_group_id            = aws_security_group.edge.id
  description                  = "Proxy to the frontend (which proxies /api to the backend)"
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = var.app_port
  to_port                      = var.app_port
}

# --- app -------------------------------------------------------------------------------------

resource "aws_vpc_security_group_ingress_rule" "app_from_edge" {
  security_group_id            = aws_security_group.app.id
  description                  = "Frontend, from the edge box only"
  referenced_security_group_id = aws_security_group.edge.id
  ip_protocol                  = "tcp"
  from_port                    = var.app_port
  to_port                      = var.app_port
}

# 443 to anywhere: the next hop is the edge box (NAT) or the S3 endpoint. Which *names* the app may
# reach is DNS Firewall's job (dns_firewall.tf); security groups can't filter by name.
resource "aws_vpc_security_group_egress_rule" "app_https_out" {
  security_group_id = aws_security_group.app.id
  description       = "HTTPS out via the edge box or the S3 endpoint"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "app_to_db" {
  security_group_id            = aws_security_group.app.id
  description                  = "Postgres"
  referenced_security_group_id = aws_security_group.db.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

# --- db: in from the app tier only, nothing out (CR-3) ----------------------------------------

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  description                  = "Postgres from the app tier only"
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
