# G7 self-test: Terraform that breaks the cloud security requirements on purpose
# (docs/security/cloud-security-requirements.md). Never applied: no provider credentials, no
# backend, and nothing points at it except the self-test. Every resource here must be caught;
# the expected check IDs are in ../expected-failures.txt.

# CR-1: a long-lived access key for a human-style IAM user.
resource "aws_iam_user" "deployer" {
  name = "deployer"
}

resource "aws_iam_access_key" "deployer" {
  user = aws_iam_user.deployer.name
}

# CR-5: admin rights on everything.
resource "aws_iam_policy" "too_broad" {
  name = "too-broad"
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "*", Resource = "*" }]
  })
}

# CR-1 / CR-6: GitHub OIDC trust that any repo on GitHub can assume.
resource "aws_iam_role" "ci" {
  name = "ci"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = "arn:aws:iam::111122223333:oidc-provider/token.actions.githubusercontent.com" }
      Condition = {
        StringLike = { "token.actions.githubusercontent.com:sub" = "repo:*" }
      }
    }]
  })
}

# CR-3 / CR-4: a public, unencrypted database.
resource "aws_db_instance" "db" {
  identifier          = "sevacenter"
  engine              = "postgres"
  instance_class      = "db.t4g.micro"
  allocated_storage   = 20
  username            = "app"
  manage_master_user_password = true
  publicly_accessible = true
  storage_encrypted   = false
  skip_final_snapshot = true
}

# CR-3: SSH and Postgres open to the internet.
resource "aws_security_group" "open" {
  name        = "open"
  description = "wide open"

  ingress {
    description = "ssh"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "postgres"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# CR-4: a bucket for receipts with public access allowed.
resource "aws_s3_bucket" "receipts" {
  bucket = "sevacenter-receipts-example"
}

resource "aws_s3_bucket_public_access_block" "receipts" {
  bucket                  = aws_s3_bucket.receipts.id
  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

# CKV_SEVA_1 cases, the other two ways Terraform writes ingress. Must FAIL:
resource "aws_vpc_security_group_ingress_rule" "db_from_ipv6_internet" {
  security_group_id = aws_security_group.open.id
  cidr_ipv6         = "::/0"
  from_port         = 5432
  to_port           = 5432
  ip_protocol       = "tcp"
}

resource "aws_security_group_rule" "all_ports" {
  type              = "ingress"
  security_group_id = aws_security_group.open.id
  cidr_blocks       = ["0.0.0.0/0"]
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
}

# Must PASS CKV_SEVA_1: the load balancer, web ports only; egress is out of scope; a private CIDR.
resource "aws_security_group" "alb" {
  name        = "alb"
  description = "load balancer"

  ingress {
    description = "https"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "http, redirected to https"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group_rule" "egress_anywhere" {
  type              = "egress"
  security_group_id = aws_security_group.alb.id
  cidr_blocks       = ["0.0.0.0/0"]
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "db_from_vpc" {
  security_group_id = aws_security_group.open.id
  cidr_ipv4         = "10.0.0.0/16"
  from_port         = 5432
  to_port           = 5432
  ip_protocol       = "tcp"
}
