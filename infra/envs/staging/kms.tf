# One customer-managed key for the whole environment (CR-4): ~$1/month, where a key per service
# would multiply that for little gain at this size. Later rounds add statements for RDS, Secrets
# Manager and CloudWatch Logs as those resources arrive; each service gets only what it needs.

data "aws_caller_identity" "current" {}

resource "aws_kms_key" "this" {
  description             = "${local.name}: encryption at rest for personal data and logs (CR-4)"
  enable_key_rotation     = true
  rotation_period_in_days = 365
  deletion_window_in_days = 30 # a scheduled deletion can be cancelled for a month
  policy                  = data.aws_iam_policy_document.kms.json
}

resource "aws_kms_alias" "this" {
  name          = "alias/${local.name}"
  target_key_id = aws_kms_key.this.key_id
}

data "aws_iam_policy_document" "kms" {
  # Account-level control: without this statement no IAM policy could ever grant use of the key,
  # and a mistake in this document would lock the key forever. IAM policies decide who in the
  # account may use it (CR-5).
  statement {
    sid       = "AccountAdministersThroughIam"
    actions   = ["kms:*"]
    resources = ["*"] # in a key policy, "*" means this key
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }

  # VPC flow logs delivered to S3 are encrypted with this key by the log delivery service.
  statement {
    sid       = "FlowLogDelivery"
    actions   = ["kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:DescribeKey"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["delivery.logs.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}
