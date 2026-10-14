# GitHub Actions -> AWS without stored keys (CR-6, ADR 0003): a workflow presents GitHub's signed
# OIDC token and STS returns credentials for one hour.
#
# An account can hold one provider per issuer URL. If it already exists (created by hand for the
# earlier staging deploy), the import block below brings it under Terraform instead of failing on
# a duplicate. If the account has none, delete the import block before the first apply.

import {
  to = aws_iam_openid_connect_provider.github
  id = "${local.arn_prefix}:iam::${local.account_id}:oidc-provider/token.actions.githubusercontent.com"
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  # Other roles (the existing staging deploy role) trust this provider too.
  lifecycle {
    prevent_destroy = true
  }
}

locals {
  oidc = "token.actions.githubusercontent.com"
}

# --- plan: pull requests of this repository --------------------------------------------------

data "aws_iam_policy_document" "plan_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${local.oidc}:aud"
      values   = ["sts.amazonaws.com"]
    }
    # Only pull_request runs of this repo. Forks' pull_request runs get no OIDC token at all.
    condition {
      test     = "StringEquals"
      variable = "${local.oidc}:sub"
      values   = ["repo:${var.github_repository}:pull_request"]
    }
  }
}

resource "aws_iam_role" "plan" {
  name                 = "${local.prefix}-tf-plan"
  description          = "terraform plan from pull requests: read-only, no data reads (ADR 0033)"
  assume_role_policy   = data.aws_iam_policy_document.plan_trust.json
  max_session_duration = 3600
}

# --- apply: jobs in the protected GitHub environment ------------------------------------------
# AWS can only match the token's sub (not its ref), so "main only" and "a human approved this run"
# are enforced by the GitHub environment: deployment branches = main, required reviewer = AppSec.
# The sub below names the environment, so a job outside it can't assume the role.

data "aws_iam_policy_document" "apply_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${local.oidc}:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "${local.oidc}:sub"
      values   = ["repo:${var.github_repository}:environment:${var.apply_environment}"]
    }
  }
}

resource "aws_iam_role" "apply" {
  name                 = "${local.prefix}-tf-apply"
  description          = "terraform apply from the approved ${var.apply_environment} environment (ADR 0033)"
  assume_role_policy   = data.aws_iam_policy_document.apply_trust.json
  max_session_duration = 3600
}
