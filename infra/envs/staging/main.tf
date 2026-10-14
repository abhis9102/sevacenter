# Staging (ADR 0032). Values that identify us (account, domain, alert email) come from a tfvars
# file or TF_VAR_* that is never committed; see terraform.tfvars.example.

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Partial configuration: bucket and key are passed at `terraform init -backend-config=...`, so
  # the state bucket's name (which carries the account ID) isn't committed. The bucket itself is
  # the next round. use_lockfile = S3-native locking, no DynamoDB table.
  backend "s3" {
    region       = "ap-south-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "sevacenter"
      Environment = "staging"
      ManagedBy   = "terraform"
    }
  }
}

locals {
  name = "sevacenter-staging"
}

module "network" {
  source = "../../modules/network"

  name                  = local.name
  kms_key_arn           = aws_kms_key.this.arn
  extra_allowed_domains = var.extra_allowed_domains
}
