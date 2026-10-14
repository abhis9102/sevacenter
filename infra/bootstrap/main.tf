# Bootstrap (ADR 0033): what Terraform itself needs before CI can run it. Applied by a human
# through Identity Center (CR-1), never by CI: the roles CI uses can't change their own
# permissions because this stack is outside their reach.
#
# First apply (the state bucket doesn't exist yet), from infra/bootstrap/:
#   1. printf 'terraform {\n  backend "local" {}\n}\n' > backend_override.tf    (gitignored)
#   2. terraform init && terraform apply
#   3. rm backend_override.tf
#   4. terraform init -migrate-state \
#        -backend-config="bucket=$(terraform output -raw state_bucket)" -backend-config="key=bootstrap.tfstate"
# Later runs: step 4's init (without -migrate-state), then plan/apply.

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # No `encrypt = true`: without a key ID it makes the backend write SSE-S3, overriding the
  # bucket's KMS default (CR-4). The bucket encrypts with its key and refuses SSE-S3 writes.
  backend "s3" {
    region       = "ap-south-1"
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "sevacenter"
      Stack     = "bootstrap"
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  arn_prefix = "arn:${data.aws_partition.current.partition}"

  # Every IAM name this project creates starts with this, so policies can scope to it.
  prefix = "sevacenter"
}
