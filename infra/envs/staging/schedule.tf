# Staging sleeps 23:00-08:00 IST (ADR 0032): the credit pays for about 15 hours a day. The database
# starts first and stops last, so the app never runs without it.

locals {
  instance_arns = [aws_instance.edge.arn, aws_instance.app.arn]
  instance_ids  = [aws_instance.edge.id, aws_instance.app.id]

  schedules = {
    start-db    = { cron = "cron(40 7 * * ? *)", target = "rds:startDBInstance", input = { DbInstanceIdentifier = aws_db_instance.this.identifier } }
    start-boxes = { cron = "cron(0 8 * * ? *)", target = "ec2:startInstances", input = { InstanceIds = local.instance_ids } }
    stop-boxes  = { cron = "cron(0 23 * * ? *)", target = "ec2:stopInstances", input = { InstanceIds = local.instance_ids } }
    stop-db     = { cron = "cron(10 23 * * ? *)", target = "rds:stopDBInstance", input = { DbInstanceIdentifier = aws_db_instance.this.identifier } }
  }
}

data "aws_iam_policy_document" "scheduler_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name                 = "${local.name}-night-schedule"
  path                 = local.workload_path
  assume_role_policy   = data.aws_iam_policy_document.scheduler_trust.json
  permissions_boundary = local.workload_boundary
}

data "aws_iam_policy_document" "scheduler" {
  statement {
    sid       = "Boxes"
    actions   = ["ec2:StartInstances", "ec2:StopInstances"]
    resources = local.instance_arns
  }
  statement {
    sid       = "Database"
    actions   = ["rds:StartDBInstance", "rds:StopDBInstance"]
    resources = [aws_db_instance.this.arn]
  }
  # Starting instances whose volumes use the environment key: a grant for EBS only.
  statement {
    sid       = "EbsGrant"
    actions   = ["kms:CreateGrant"]
    resources = [aws_kms_key.this.arn]
    condition {
      test     = "Bool"
      variable = "kms:GrantIsForAWSResource"
      values   = ["true"]
    }
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "night-schedule"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.scheduler.json
}

resource "aws_scheduler_schedule" "night" {
  for_each = local.schedules

  name                         = "${local.name}-${each.key}"
  schedule_expression          = each.value.cron
  schedule_expression_timezone = "Asia/Kolkata"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:${each.value.target}"
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode(each.value.input)

    retry_policy {
      maximum_retry_attempts = 3
    }
  }
}
