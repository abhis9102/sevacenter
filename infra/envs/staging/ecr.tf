# Image repositories (ADR 0034). Our two images, plus the two third-party images staging runs
# (Caddy on the edge box, Mailpit as the mail catcher), mirrored here by digest by the deploy
# workflow: the boxes pull only from this account, so no public registry is on the DNS allowlist.

locals {
  repositories = ["backend", "frontend", "caddy", "mailpit"]
}

resource "aws_ecr_repository" "this" {
  for_each = toset(local.repositories)

  name                 = "sevacenter-${each.key}"
  image_tag_mutability = "IMMUTABLE" # a tag always means the same image; deploys pin digests anyway

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.this.arn
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 15 images: enough to roll back, storage stays small"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 15
      }
      action = { type = "expire" }
    }]
  })
}
