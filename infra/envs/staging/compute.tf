# The two boxes (ADR 0034): edge (Caddy + NAT, public subnet) and app (containers, private subnet).
# x86 (t3) because the images are built for amd64. No SSH key on either: access is Session Manager.

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

locals {
  workload_path     = "/sevacenter/workload/"
  workload_boundary = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/sevacenter/sevacenter-workload-boundary"

  app_ip   = cidrhost(local.app_cidr0, 10) # fixed, so the edge box's Caddyfile can name it
  app_cidr = "172.16.10.0/23"              # both app subnets
  # module outputs are IDs; the CIDR of the workload-AZ app subnet is fixed by the layout (ADR 0032)
  app_cidr0 = "172.16.10.0/24"

  template_vars = {
    region      = var.region
    account_id  = data.aws_caller_identity.current.account_id
    staff_base  = var.staff_base_domain
    mandir_base = var.mandir_base_domain
    app_ip      = local.app_ip
    app_cidr    = local.app_cidr
    # X-Forwarded-For is believed only from the frontend container, on the pinned docker network
    # (app-redeploy.sh). ".*" trusted any peer: harmless only while nothing forwarded the header.
    trusted_proxies = "172\\.30\\.0\\.\\d{1,3}"
    app_net_cidr    = "172.30.0.0/24"
  }

  # Run at every boot; retried until the dependencies (edge NAT, image pins) are there.
  unit = <<-EOT
    [Unit]
    Description=SevaCenter containers
    Wants=network-online.target
    After=network-online.target docker.service
    Requires=docker.service

    [Service]
    Type=oneshot
    RemainAfterExit=yes
    ExecStart=/opt/sevacenter/start.sh
    Restart=on-failure
    RestartSec=30

    [Install]
    WantedBy=multi-user.target
  EOT
}

# --- roles ---------------------------------------------------------------------------------------

data "aws_iam_policy_document" "ec2_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "box" {
  for_each = toset(["edge", "app"])

  name                 = "${local.name}-${each.key}"
  path                 = local.workload_path
  assume_role_policy   = data.aws_iam_policy_document.ec2_trust.json
  permissions_boundary = local.workload_boundary
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  for_each   = aws_iam_role.box
  role       = each.value.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "box" {
  for_each = toset(["edge", "app"])

  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"] # no resource-level permission for this action
  }

  statement {
    sid       = "PullImages"
    actions   = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"]
    resources = each.key == "edge" ? [aws_ecr_repository.this["caddy"].arn] : [for r in ["backend", "frontend", "mailpit"] : aws_ecr_repository.this[r].arn]
  }

  statement {
    sid       = "ImagePins"
    actions   = ["ssm:GetParameter"]
    resources = each.key == "edge" ? [aws_ssm_parameter.image["caddy"].arn] : [for r in ["backend", "frontend", "mailpit"] : aws_ssm_parameter.image[r].arn]
  }

  statement {
    sid       = "EnvironmentKey"
    actions   = ["kms:Decrypt"]
    resources = [aws_kms_key.this.arn]
  }

  # AmazonSSMManagedInstanceCore allows ssm:GetParameter on "*", and the edge box can use the
  # environment key (for its image): without this, the internet-facing box could read the app's
  # secrets.
  dynamic "statement" {
    for_each = each.key == "edge" ? [1] : []
    content {
      sid     = "EdgeNeverReadsAppSecrets"
      effect  = "Deny"
      actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "secretsmanager:GetSecretValue"]
      resources = [
        "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/sevacenter/staging/app",
        "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/sevacenter/staging/app/*",
        "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/sevacenter/staging/db/*",
        "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:*",
      ]
    }
  }

  dynamic "statement" {
    for_each = each.key == "app" ? [1] : []
    content {
      sid     = "AppConfigAndSecrets"
      actions = ["ssm:GetParameter", "ssm:GetParametersByPath"]
      resources = [
        "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/sevacenter/staging/app",
        "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/sevacenter/staging/app/*",
        aws_ssm_parameter.db_endpoint.arn,
        aws_ssm_parameter.db_master_secret.arn,
      ]
    }
  }

  dynamic "statement" {
    for_each = each.key == "app" ? [1] : []
    content {
      sid       = "DatabaseOwnerSecret"
      actions   = ["secretsmanager:GetSecretValue"]
      resources = [aws_db_instance.this.master_user_secret[0].secret_arn]
    }
  }
}

resource "aws_iam_role_policy" "box" {
  for_each = aws_iam_role.box
  name     = "box"
  role     = each.value.id
  policy   = data.aws_iam_policy_document.box[each.key].json
}

resource "aws_iam_instance_profile" "box" {
  for_each = aws_iam_role.box
  name     = each.value.name
  path     = local.workload_path
  role     = each.value.name
}

# --- instances -----------------------------------------------------------------------------------

resource "aws_instance" "edge" {
  ami                    = data.aws_ssm_parameter.al2023.value
  instance_type          = "t3.micro" # free-plan accounts may launch free-tier-eligible types only
  subnet_id              = module.network.subnet_ids.public[0]
  vpc_security_group_ids = [module.network.security_group_ids.edge]
  iam_instance_profile   = aws_iam_instance_profile.box["edge"].name
  source_dest_check      = false # it forwards the app tier's traffic (NAT)
  monitoring             = false # detailed monitoring is billed; basic metrics are enough here
  ebs_optimized          = true

  metadata_options {
    http_tokens                 = "required" # IMDSv2 only
    http_put_response_hop_limit = 1          # containers can't reach the instance credentials
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 8
    encrypted   = true
    kms_key_id  = aws_kms_key.this.arn
  }

  # A change to the boot scripts replaces the box (it holds no data) rather than leaving the old
  # script on disk: cloud-init runs once per instance.
  user_data_replace_on_change = true
  user_data                   = <<-EOT
    #cloud-config
    packages: [docker, iptables-nft]
    write_files:
      - path: /opt/sevacenter/start.sh
        permissions: "0700"
        encoding: b64
        content: ${base64encode(templatefile("${path.module}/files/edge-start.sh.tftpl", local.template_vars))}
      - path: /opt/sevacenter/Caddyfile
        permissions: "0644"
        encoding: b64
        content: ${base64encode(templatefile("${path.module}/files/Caddyfile.tftpl", local.template_vars))}
      - path: /etc/systemd/system/sevacenter.service
        encoding: b64
        content: ${base64encode(local.unit)}
    runcmd:
      - systemctl enable --now docker
      - systemctl enable --now sevacenter.service
  EOT

  tags = { Name = "${local.name}-edge", "sevacenter-role" = "edge" }

  lifecycle {
    ignore_changes = [ami] # a newer AMI is picked up deliberately, not on every plan
  }
}

resource "aws_eip" "edge" {
  domain   = "vpc"
  instance = aws_instance.edge.id
  tags     = { Name = "${local.name}-edge" }
}

resource "aws_route" "app_default_via_edge" {
  route_table_id         = module.network.app_route_table_id
  destination_cidr_block = "0.0.0.0/0"
  network_interface_id   = aws_instance.edge.primary_network_interface_id
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023.value
  instance_type          = "t3.micro"
  subnet_id              = module.network.subnet_ids.app[0]
  private_ip             = local.app_ip
  vpc_security_group_ids = [module.network.security_group_ids.app]
  iam_instance_profile   = aws_iam_instance_profile.box["app"].name
  monitoring             = false
  ebs_optimized          = true

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 16
    encrypted   = true
    kms_key_id  = aws_kms_key.this.arn
  }

  user_data_replace_on_change = true
  user_data                   = <<-EOT
    #cloud-config
    packages: [docker, jq]
    write_files:
      - path: /opt/sevacenter/start.sh
        permissions: "0700"
        encoding: b64
        content: ${base64encode(templatefile("${path.module}/files/app-redeploy.sh.tftpl", local.template_vars))}
      - path: /etc/systemd/system/sevacenter.service
        encoding: b64
        content: ${base64encode(local.unit)}
    runcmd:
      - fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /etc/fstab
      - systemctl enable --now docker
      - systemctl enable --now sevacenter.service
  EOT

  tags = { Name = "${local.name}-app", "sevacenter-role" = "app" }

  lifecycle {
    ignore_changes = [ami]
  }

  depends_on = [aws_route.app_default_via_edge]
}

# The one thing the deploy role may run on the app box.
resource "aws_ssm_document" "redeploy" {
  name            = "sevacenter-staging-redeploy"
  document_type   = "Command"
  document_format = "YAML"
  content         = <<-EOT
    schemaVersion: "2.2"
    description: Restart the SevaCenter containers from the pinned image digests (ADR 0034)
    mainSteps:
      - action: aws:runShellScript
        name: redeploy
        inputs:
          timeoutSeconds: "600"
          runCommand:
            - /opt/sevacenter/start.sh
  EOT
}

# Image pins, written by the deploy workflow. Terraform creates them and then leaves the value alone.
resource "aws_ssm_parameter" "image" {
  for_each = toset(local.repositories)

  name  = "/sevacenter/staging/images/${each.key}"
  type  = "String"
  value = "unset"

  lifecycle {
    ignore_changes = [value]
  }
}
