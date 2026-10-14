# Permissions of the two CI roles and the boundary every workload role must carry (ADR 0033, CR-5).

locals {
  state_bucket = aws_s3_bucket.state.arn

  # Services with no region (or whose API lives in us-east-1): the region guard can't apply.
  global_actions = ["iam:*", "sts:*", "route53:*", "budgets:*", "ce:*", "support:*", "health:*"]

  # Workload roles and their policies live under this IAM path. The apply role may only touch
  # IAM resources here: the CI roles and the boundary itself sit outside it, so the role can't
  # edit its own permissions or loosen the boundary.
  workload_path = "/${local.prefix}/workload/"
}

# --- shared: region guard (CR-2) and state access -------------------------------------------

data "aws_iam_policy_document" "region_guard" {
  statement {
    sid         = "DenyOutsideHomeRegion"
    effect      = "Deny"
    not_actions = local.global_actions
    resources   = ["*"]
    condition {
      test     = "StringNotEquals"
      variable = "aws:RequestedRegion"
      values   = [var.region]
    }
  }
}

resource "aws_iam_policy" "region_guard" {
  name        = "${local.prefix}-region-guard"
  path        = "/${local.prefix}/"
  description = "CR-2: deny every regional action outside ${var.region}"
  policy      = data.aws_iam_policy_document.region_guard.json
}

data "aws_iam_policy_document" "state_rw" {
  statement {
    sid       = "ListState"
    actions   = ["s3:ListBucket"]
    resources = [local.state_bucket]
  }
  statement {
    sid       = "ReadWriteStateAndLocks"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${local.state_bucket}/*"]
  }
  statement {
    sid       = "StateKey"
    actions   = ["kms:Decrypt", "kms:Encrypt", "kms:GenerateDataKey"]
    resources = [aws_kms_key.state.arn]
  }
}

# Plan reads state and writes only its lock file. With state_rw a malicious PR could rewrite the
# state and steer the next (approved) apply.
data "aws_iam_policy_document" "state_read_lock" {
  statement {
    sid       = "ListState"
    actions   = ["s3:ListBucket"]
    resources = [local.state_bucket]
  }
  statement {
    sid       = "ReadState"
    actions   = ["s3:GetObject"]
    resources = ["${local.state_bucket}/*"]
  }
  statement {
    sid       = "LockFilesOnly"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${local.state_bucket}/*.tflock"]
  }
  statement {
    sid       = "StateKey"
    actions   = ["kms:Decrypt", "kms:Encrypt", "kms:GenerateDataKey"]
    resources = [aws_kms_key.state.arn]
  }
}

resource "aws_iam_policy" "state_read_lock" {
  name        = "${local.prefix}-tfstate-read-lock"
  path        = "/${local.prefix}/"
  description = "Read Terraform state, write only lock files (plan)"
  policy      = data.aws_iam_policy_document.state_read_lock.json
}

resource "aws_iam_policy" "state_rw" {
  name        = "${local.prefix}-tfstate-rw"
  path        = "/${local.prefix}/"
  description = "Read and write Terraform state and its lock files (apply)"
  policy      = data.aws_iam_policy_document.state_rw.json
}

# --- plan: read configuration, never data ---------------------------------------------------
# `terraform plan` runs the PR's code (providers, data sources), so a malicious branch acts with
# this role. AWS's ReadOnlyAccess also reads data (S3 objects, log events, secret values,
# container images); the deny below removes those, leaving what a plan needs: resource configs.
# Plan writes only lock files next to the state (state_read_lock); nothing else is writable.

data "aws_iam_policy_document" "plan_no_data" {
  statement {
    sid    = "DenyDataReads"
    effect = "Deny"
    actions = [
      "athena:GetQueryResults",
      "dynamodb:BatchGetItem", "dynamodb:GetItem", "dynamodb:Query", "dynamodb:Scan",
      "ec2:GetConsoleOutput", "ec2:GetConsoleScreenshot", "ec2:GetPasswordData",
      "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer",
      "lambda:GetFunction",
      "logs:FilterLogEvents", "logs:GetLogEvents", "logs:GetQueryResults", "logs:StartQuery",
      "rds:DownloadCompleteDBLogFile", "rds:DownloadDBLogFilePortion",
      "secretsmanager:GetSecretValue",
      "sqs:ReceiveMessage",
      "ssm:GetParameter", "ssm:GetParameterHistory", "ssm:GetParameters", "ssm:GetParametersByPath",
    ]
    resources = ["*"]
  }

  statement {
    sid           = "DenyObjectReadsOutsideState"
    effect        = "Deny"
    actions       = ["s3:GetObject", "s3:GetObjectVersion"]
    not_resources = ["${local.state_bucket}/*"]
  }

  statement {
    sid           = "DenyDecryptOutsideState"
    effect        = "Deny"
    actions       = ["kms:Decrypt"]
    not_resources = [aws_kms_key.state.arn]
  }
}

resource "aws_iam_role_policy" "plan_no_data" {
  name   = "no-data-reads"
  role   = aws_iam_role.plan.id
  policy = data.aws_iam_policy_document.plan_no_data.json
}

resource "aws_iam_role_policy_attachment" "plan" {
  for_each = {
    readonly     = "${local.arn_prefix}:iam::aws:policy/ReadOnlyAccess"
    region_guard = aws_iam_policy.region_guard.arn
    state        = aws_iam_policy.state_read_lock.arn
  }
  role       = aws_iam_role.plan.name
  policy_arn = each.value
}

# --- apply: build what the stacks declare, nothing more ---------------------------------------
# Service-wide actions where AWS has no resource-level permissions for creation (a VPC or a KMS
# key has no ARN before it exists); named resources wherever it does. Each `"*"` is commented.

data "aws_iam_policy_document" "apply" {
  # Network, instances, scheduling, logs, budgets: created resources have generated IDs, so they
  # can't be named in advance. The region guard keeps them in ap-south-1.
  statement {
    sid = "ServicesWithGeneratedIds"
    actions = [
      "ec2:*",
      "route53resolver:*",
      "logs:*",
      "budgets:*",
      "scheduler:*",
      "rds:*",
    ]
    resources = ["*"] # IDs are generated at creation; scoped by the region guard
  }

  statement {
    sid       = "ProjectBuckets"
    actions   = ["s3:*"]
    resources = ["arn:aws:s3:::${local.prefix}-*", "arn:aws:s3:::${local.prefix}-*/*"]
  }

  # The state bucket is the bootstrap's: object access only (state_rw), never its configuration.
  statement {
    sid         = "StateBucketConfigIsNotApplys"
    effect      = "Deny"
    not_actions = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket", "s3:GetBucketLocation"]
    resources   = [local.state_bucket, "${local.state_bucket}/*"]
  }

  statement {
    sid     = "ProjectRepositoriesAndParameters"
    actions = ["ecr:*", "ssm:*", "secretsmanager:*"]
    resources = [
      "arn:aws:ecr:${var.region}:${local.account_id}:repository/${local.prefix}-*",
      "arn:aws:ssm:${var.region}:${local.account_id}:parameter/${local.prefix}/*",
      "arn:aws:ssm:${var.region}:${local.account_id}:document/${local.prefix}-*",
      "arn:aws:secretsmanager:${var.region}:${local.account_id}:secret:${local.prefix}-*",
      # RDS creates the master user's secret itself (manage_master_user_password), named rds!db-*.
      "arn:aws:secretsmanager:${var.region}:${local.account_id}:secret:rds!*",
    ]
  }

  statement {
    sid       = "KmsCreate"
    actions   = ["kms:CreateKey", "kms:ListAliases", "kms:ListKeys"]
    resources = ["*"] # no resource-level permissions for these actions
  }

  statement {
    sid = "KmsManageOwnKeys"
    actions = [
      "kms:CreateAlias", "kms:DeleteAlias", "kms:DescribeKey", "kms:EnableKeyRotation",
      "kms:GetKeyPolicy", "kms:GetKeyRotationStatus", "kms:ListResourceTags", "kms:PutKeyPolicy",
      "kms:ScheduleKeyDeletion", "kms:TagResource", "kms:UntagResource", "kms:UpdateAlias",
      "kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey", "kms:CreateGrant",
      # EBS encryption under our key (instance volumes).
      "kms:GenerateDataKeyWithoutPlaintext", "kms:ReEncryptFrom", "kms:ReEncryptTo",
    ]
    resources = [
      "arn:aws:kms:${var.region}:${local.account_id}:key/*",
      "arn:aws:kms:${var.region}:${local.account_id}:alias/${local.prefix}-*",
    ]
  }

  # Never the state key's policy, rotation or lifetime.
  statement {
    sid       = "StateKeyIsNotApplys"
    effect    = "Deny"
    actions   = ["kms:PutKeyPolicy", "kms:ScheduleKeyDeletion", "kms:DisableKey", "kms:DisableKeyRotation", "kms:CreateGrant"]
    resources = [aws_kms_key.state.arn]
  }

  # --- IAM: only under the workload path, and every role born with the boundary ---
  statement {
    sid       = "CreateBoundedRoles"
    actions   = ["iam:CreateRole", "iam:PutRolePermissionsBoundary", "iam:AttachRolePolicy", "iam:PutRolePolicy"]
    resources = ["arn:aws:iam::${local.account_id}:role${local.workload_path}*"]
    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [aws_iam_policy.workload_boundary.arn]
    }
  }

  statement {
    sid = "ManageWorkloadIam"
    actions = [
      "iam:DeleteRole", "iam:DeleteRolePolicy", "iam:DetachRolePolicy", "iam:TagRole", "iam:UntagRole",
      "iam:UpdateRole", "iam:UpdateRoleDescription", "iam:UpdateAssumeRolePolicy",
      "iam:CreatePolicy", "iam:CreatePolicyVersion", "iam:DeletePolicy", "iam:DeletePolicyVersion",
      "iam:TagPolicy", "iam:UntagPolicy",
      "iam:CreateInstanceProfile", "iam:DeleteInstanceProfile", "iam:AddRoleToInstanceProfile",
      "iam:RemoveRoleFromInstanceProfile", "iam:TagInstanceProfile", "iam:UntagInstanceProfile",
    ]
    resources = [
      "arn:aws:iam::${local.account_id}:role${local.workload_path}*",
      "arn:aws:iam::${local.account_id}:policy${local.workload_path}*",
      "arn:aws:iam::${local.account_id}:instance-profile${local.workload_path}*",
    ]
  }

  statement {
    sid       = "PassWorkloadRolesToTheirServices"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${local.account_id}:role${local.workload_path}*"]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ec2.amazonaws.com", "scheduler.amazonaws.com", "monitoring.rds.amazonaws.com"]
    }
  }

  statement {
    sid       = "NeverRemoveTheBoundary"
    effect    = "Deny"
    actions   = ["iam:DeleteRolePermissionsBoundary"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "apply" {
  name        = "${local.prefix}-tf-apply"
  path        = "/${local.prefix}/"
  description = "What terraform apply may build (ADR 0033)"
  policy      = data.aws_iam_policy_document.apply.json
}

resource "aws_iam_role_policy_attachment" "apply" {
  for_each = {
    readonly     = "${local.arn_prefix}:iam::aws:policy/ReadOnlyAccess" # refresh reads every resource it manages
    apply        = aws_iam_policy.apply.arn
    region_guard = aws_iam_policy.region_guard.arn
    state        = aws_iam_policy.state_rw.arn
  }
  role       = aws_iam_role.apply.name
  policy_arn = each.value
}

# --- the ceiling for every role apply creates -------------------------------------------------
# A permissions boundary caps what a role can ever do, whatever policies are attached to it. Without
# it, a role that can create roles can create an administrator and use it.

data "aws_iam_policy_document" "workload_boundary" {
  statement {
    sid = "SessionManagerAgent"
    actions = [
      "ssm:DescribeAssociation", "ssm:GetDeployablePatchSnapshotForInstance", "ssm:GetDocument",
      "ssm:DescribeDocument", "ssm:GetManifest", "ssm:ListAssociations", "ssm:ListInstanceAssociations",
      "ssm:PutInventory", "ssm:PutComplianceItems", "ssm:PutConfigurePackageResult",
      "ssm:UpdateAssociationStatus", "ssm:UpdateInstanceAssociationStatus", "ssm:UpdateInstanceInformation",
      "ssmmessages:CreateControlChannel", "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel", "ssmmessages:OpenDataChannel",
      "ec2messages:AcknowledgeMessage", "ec2messages:DeleteMessage", "ec2messages:FailMessage",
      "ec2messages:GetEndpoint", "ec2messages:GetMessages", "ec2messages:SendReply",
    ]
    resources = ["*"] # the agent's own API: no resource-level permissions
  }

  statement {
    sid       = "PullImages"
    actions   = ["ecr:GetAuthorizationToken", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"]
    resources = ["*"] # GetAuthorizationToken has no resource; the role policy names the repositories
  }

  statement {
    sid       = "WriteLogsAndMetrics"
    actions   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams", "cloudwatch:PutMetricData"]
    resources = ["*"] # narrowed by each role's own policy
  }

  statement {
    sid     = "UseKeysAndOwnSecrets"
    actions = ["kms:Decrypt", "kms:Encrypt", "kms:GenerateDataKey", "kms:DescribeKey", "secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret", "ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = [
      "arn:aws:kms:${var.region}:${local.account_id}:key/*",
      "arn:aws:secretsmanager:${var.region}:${local.account_id}:secret:${local.prefix}-*",
      "arn:aws:secretsmanager:${var.region}:${local.account_id}:secret:rds!*", # the RDS-managed master secret
      "arn:aws:ssm:${var.region}:${local.account_id}:parameter/${local.prefix}/*",
    ]
  }

  statement {
    sid       = "ProjectBucketObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:ListBucket"]
    resources = ["arn:aws:s3:::${local.prefix}-*", "arn:aws:s3:::${local.prefix}-*/*"]
  }

  # Starting an instance whose volumes use our key needs a grant for EBS, only for an AWS service.
  statement {
    sid       = "GrantsForAwsServices"
    actions   = ["kms:CreateGrant"]
    resources = ["arn:aws:kms:${var.region}:${local.account_id}:key/*"]
    condition {
      test     = "Bool"
      variable = "kms:GrantIsForAWSResource"
      values   = ["true"]
    }
  }

  # The night schedule (ADR 0032) stops and starts the boxes and the database.
  statement {
    sid     = "NightSchedule"
    actions = ["ec2:StartInstances", "ec2:StopInstances", "rds:StartDBInstance", "rds:StopDBInstance"]
    resources = [
      "arn:aws:ec2:${var.region}:${local.account_id}:instance/*",
      "arn:aws:rds:${var.region}:${local.account_id}:db:${local.prefix}-*",
    ]
  }

  # The state bucket is never a workload's.
  statement {
    sid       = "NotTheState"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [local.state_bucket, "${local.state_bucket}/*"]
  }
}

resource "aws_iam_policy" "workload_boundary" {
  name        = "${local.prefix}-workload-boundary"
  path        = "/${local.prefix}/"
  description = "Permissions boundary: the most any workload role can do (ADR 0033)"
  policy      = data.aws_iam_policy_document.workload_boundary.json
}
