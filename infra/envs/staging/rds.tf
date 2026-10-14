# The database (ADR 0034, CR-3, CR-4): private, encrypted with the environment key, TLS required,
# master password generated and kept by RDS in Secrets Manager (never in Terraform state).

resource "aws_db_subnet_group" "this" {
  name       = local.name
  subnet_ids = module.network.subnet_ids.db # two AZs: an RDS requirement, the instance runs in 1a
}

resource "aws_db_parameter_group" "this" {
  name   = "${local.name}-pg16"
  family = "postgres16"

  parameter {
    name  = "rds.force_ssl"
    value = "1" # the server refuses unencrypted connections; the client verifies the certificate
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # slow queries only: statements can carry personal data, so not every one
  }
}

resource "aws_cloudwatch_log_group" "rds" {
  name              = "/aws/rds/instance/${local.name}/postgresql"
  retention_in_days = 180 # CERT-In
  kms_key_id        = aws_kms_key.this.arn
}

resource "aws_db_instance" "this" {
  identifier     = local.name
  engine         = "postgres"
  engine_version = "16"
  instance_class = "db.t4g.micro"

  db_name  = "sevacenter"
  username = "sevacenter" # the owner: Flyway migrates as it; the app runs as sevacenter_app (RLS)

  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.this.arn

  allocated_storage     = 20
  max_allocated_storage = 30 # small cap: autoscaling storage can't run up the bill
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.this.arn

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [module.network.security_group_ids.db]
  availability_zone      = "ap-south-1a" # next to the app box
  publicly_accessible    = false
  multi_az               = false
  parameter_group_name   = aws_db_parameter_group.this.name

  iam_database_authentication_enabled = true
  enabled_cloudwatch_logs_exports     = ["postgresql"]

  backup_retention_period    = 7
  backup_window              = "20:00-20:30" # UTC = 01:30 IST, while staging sleeps
  maintenance_window         = "sun:21:00-sun:21:30"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${local.name}-final"

  depends_on = [aws_cloudwatch_log_group.rds]
}

# Where the app box finds the database: names only, the password stays in Secrets Manager.
resource "aws_ssm_parameter" "db_endpoint" {
  name  = "/sevacenter/staging/db/endpoint"
  type  = "String"
  value = aws_db_instance.this.endpoint
}

resource "aws_ssm_parameter" "db_master_secret" {
  name  = "/sevacenter/staging/db/master-secret-arn"
  type  = "String"
  value = aws_db_instance.this.master_user_secret[0].secret_arn
}
