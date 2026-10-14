# VPC flow logs (ADR 0032): every connection's metadata (who, to where, port, bytes, accepted or
# rejected), never packet contents. Kept in S3 in ap-south-1, encrypted with the environment's
# customer-managed key (CR-4), deleted after var.flow_log_retention_days (CERT-In: 180 days).

resource "aws_flow_log" "this" {
  vpc_id                   = aws_vpc.this.id
  traffic_type             = "ALL" # incidents ask what was ACCEPTED, not only what was blocked
  max_aggregation_interval = 60    # same price as 600 (billed per GB), sharper timeline
  log_destination_type     = "s3"
  log_destination          = aws_s3_bucket.flow_logs.arn
  # v5 fields: pkt-srcaddr/pkt-dstaddr show the real peer behind the NAT, flow-direction and
  # tcp-flags tell a reply from a new connection.
  log_format = "$${version} $${account-id} $${interface-id} $${srcaddr} $${dstaddr} $${srcport} $${dstport} $${protocol} $${packets} $${bytes} $${start} $${end} $${action} $${log-status} $${pkt-srcaddr} $${pkt-dstaddr} $${flow-direction} $${tcp-flags}"

  destination_options {
    file_format                = "parquet" # smaller, and Athena reads only the columns it needs
    per_hour_partition         = true
    hive_compatible_partitions = true
  }

  tags = { Name = var.name }
}

resource "aws_s3_bucket" "flow_logs" {
  bucket = "${var.name}-flow-logs-${data.aws_caller_identity.current.account_id}"
  tags   = { Name = "${var.name}-flow-logs" }
}

resource "aws_s3_bucket_public_access_block" "flow_logs" {
  bucket                  = aws_s3_bucket.flow_logs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "flow_logs" {
  bucket = aws_s3_bucket.flow_logs.id
  rule {
    object_ownership = "BucketOwnerEnforced" # ACLs off: access is decided by policy alone
  }
}

resource "aws_s3_bucket_versioning" "flow_logs" {
  bucket = aws_s3_bucket.flow_logs.id
  versioning_configuration {
    status = "Enabled" # an overwritten or deleted log can be recovered until it expires
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "flow_logs" {
  bucket = aws_s3_bucket.flow_logs.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = var.kms_key_arn
    }
    bucket_key_enabled = true # one data key per bucket, not a KMS call per object: cheaper
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "flow_logs" {
  bucket = aws_s3_bucket.flow_logs.id

  rule {
    id     = "retention"
    status = "Enabled"
    filter {}
    expiration {
      days = var.flow_log_retention_days
    }
    noncurrent_version_expiration {
      noncurrent_days = 7
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

resource "aws_s3_bucket_policy" "flow_logs" {
  bucket = aws_s3_bucket.flow_logs.id
  policy = data.aws_iam_policy_document.flow_logs_bucket.json
  # The public access block must exist first, or a policy could briefly apply without it.
  depends_on = [aws_s3_bucket_public_access_block.flow_logs]
}

data "aws_iam_policy_document" "flow_logs_bucket" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.flow_logs.arn, "${aws_s3_bucket.flow_logs.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  # The log delivery service writes, only on behalf of this account's flow logs in this region
  # (confused-deputy protection).
  statement {
    sid       = "FlowLogDeliveryWrite"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.flow_logs.arn}/AWSLogs/${data.aws_caller_identity.current.account_id}/*"]
    principals {
      type        = "Service"
      identifiers = ["delivery.logs.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:aws:logs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:*"]
    }
  }

  statement {
    sid       = "FlowLogDeliveryAclCheck"
    actions   = ["s3:GetBucketAcl"]
    resources = [aws_s3_bucket.flow_logs.arn]
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
