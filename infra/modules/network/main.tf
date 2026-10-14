# Network for one environment (ADR 0032): three tiers, workloads in one AZ, a second AZ only
# where AWS requires two (load balancer, RDS subnet group).
#
#   public  172.16.0.0/24  172.16.1.0/24   edge box (TLS + NAT); route to the internet gateway
#   app     172.16.10.0/24 172.16.11.0/24  app box; default route to the edge box (compute round)
#   db      172.16.20.0/24 172.16.21.0/24  RDS; no route out of the VPC at all (CR-3)

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  azs = [var.workload_az, var.second_az]

  # tier => [cidr in workload AZ, cidr in second AZ]
  subnets = {
    public = [cidrsubnet(var.vpc_cidr, 8, 0), cidrsubnet(var.vpc_cidr, 8, 1)]
    app    = [cidrsubnet(var.vpc_cidr, 8, 10), cidrsubnet(var.vpc_cidr, 8, 11)]
    db     = [cidrsubnet(var.vpc_cidr, 8, 20), cidrsubnet(var.vpc_cidr, 8, 21)]
  }

  subnet_list = merge([
    for tier, cidrs in local.subnets : {
      for i, cidr in cidrs : "${tier}-${i}" => { tier = tier, cidr = cidr, az = local.azs[i] }
    }
  ]...)
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true # the VPC resolver: DNS Firewall filters it, RDS endpoints need it
  enable_dns_hostnames = true
  tags                 = { Name = var.name }
}

# The default security group is attached to anything launched without one. Strip every rule so a
# forgotten security group means "no traffic", not "everything in this group can talk".
resource "aws_default_security_group" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-default-unused" }
}

resource "aws_subnet" "this" {
  for_each = local.subnet_list

  vpc_id            = aws_vpc.this.id
  cidr_block        = each.value.cidr
  availability_zone = each.value.az
  # Never on, even in the public tier: the edge box gets an Elastic IP on purpose, nothing else
  # gets a public address by accident.
  map_public_ip_on_launch = false
  tags                    = { Name = "${var.name}-${each.key}", Tier = each.value.tier }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = var.name }
}

# --- Route tables: one per tier -------------------------------------------------------------

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-public" }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

# The app tier's default route points at the edge box's network interface; that route is created
# with the edge box (compute round). Until then the app tier reaches only the VPC and S3.
resource "aws_route_table" "app" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-app" }
}

# No routes beyond the VPC's local one, ever: the database can't reach the internet, and the
# internet can't reach it (CR-3).
resource "aws_route_table" "db" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-db" }
}

resource "aws_route_table_association" "this" {
  for_each = local.subnet_list

  subnet_id = aws_subnet.this[each.key].id
  route_table_id = {
    public = aws_route_table.public.id
    app    = aws_route_table.app.id
    db     = aws_route_table.db.id
  }[each.value.tier]
}

# --- S3 gateway endpoint (free) --------------------------------------------------------------
# Image layers from ECR are stored in S3: pulls go over this endpoint, not through the edge box.
# The policy narrows it to buckets in this account plus ECR's layer bucket for this region, so the
# endpoint can't be used to copy data into someone else's bucket.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.app.id]
  policy            = data.aws_iam_policy_document.s3_endpoint.json
  tags              = { Name = "${var.name}-s3" }
}

data "aws_iam_policy_document" "s3_endpoint" {
  statement {
    sid       = "OwnAccountBuckets"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:ListBucket"]
    resources = ["*"] # narrowed by the account condition: bucket ARNs carry no account ID
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }

  statement {
    sid       = "EcrImageLayers"
    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::prod-${data.aws_region.current.region}-starport-layer-bucket/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}
