# Continuous deployment to staging (ADR 0033): one role for the deploy workflow on main.
#
# It can push images to the project's repositories, pin their digests, and run ONE fixed SSM
# document (the redeploy script defined by the staging stack) on instances tagged as the app box.
# It can't run arbitrary commands, read secrets, touch Terraform state or change infrastructure:
# the earlier hand-made deploy role ran any shell script read from a parameter it could rewrite.

data "aws_iam_policy_document" "cd_trust" {
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
    # Pushes to main only: a pull request or another branch can't deploy.
    condition {
      test     = "StringEquals"
      variable = "${local.oidc}:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "cd" {
  name                 = "${local.prefix}-cd-staging"
  description          = "Deploy workflow on main: push images, pin digests, run the redeploy document (ADR 0033)"
  assume_role_policy   = data.aws_iam_policy_document.cd_trust.json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "cd" {
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"] # no resource-level permission for this action
  }

  statement {
    sid = "PushProjectImages"
    actions = [
      "ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:CompleteLayerUpload",
      "ecr:DescribeImages", "ecr:GetDownloadUrlForLayer", "ecr:InitiateLayerUpload",
      "ecr:PutImage", "ecr:UploadLayerPart",
    ]
    resources = ["arn:aws:ecr:${var.region}:${local.account_id}:repository/${local.prefix}-*"]
  }

  # Repositories are encrypted with the environment key; ECR uses it on the caller's behalf.
  statement {
    sid       = "KeyThroughEcrOnly"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
    resources = ["arn:aws:kms:${var.region}:${local.account_id}:key/*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ecr.${var.region}.amazonaws.com"]
    }
  }

  statement {
    sid       = "PinImageDigests"
    actions   = ["ssm:PutParameter", "ssm:GetParameter"]
    resources = ["arn:aws:ssm:${var.region}:${local.account_id}:parameter/${local.prefix}/staging/images/*"]
  }

  statement {
    sid       = "RunTheRedeployDocument"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:${var.region}:${local.account_id}:document/${local.prefix}-staging-redeploy"]
  }

  statement {
    sid       = "OnTheAppBoxOnly"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ec2:${var.region}:${local.account_id}:instance/*"]
    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/sevacenter-role"
      values   = ["app"]
    }
  }

  statement {
    sid       = "FollowTheCommand"
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations", "ec2:DescribeInstances"]
    resources = ["*"] # read-only status calls without resource-level permissions
  }
}

resource "aws_iam_role_policy" "cd" {
  name   = "deploy"
  role   = aws_iam_role.cd.id
  policy = data.aws_iam_policy_document.cd.json
}

resource "aws_iam_role_policy_attachment" "cd_region_guard" {
  role       = aws_iam_role.cd.name
  policy_arn = aws_iam_policy.region_guard.arn
}

output "cd_role_arn" {
  description = "GitHub secret STAGING_DEPLOY_ROLE_ARN"
  value       = aws_iam_role.cd.arn
}
