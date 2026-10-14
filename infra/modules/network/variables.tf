variable "name" {
  description = "Prefix for every resource name, e.g. sevacenter-staging"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC range; the tiers are /24s inside it at fixed offsets (ADR 0032)"
  type        = string
  default     = "172.16.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr)) && endswith(var.vpc_cidr, "/16")
    error_message = "vpc_cidr must be a /16: the subnet layout is /24s at offsets 0-1, 10-11, 20-21."
  }
}

variable "workload_az" {
  description = "The one AZ that runs workloads (edge box, app box, database primary)"
  type        = string
  default     = "ap-south-1a"
}

variable "second_az" {
  description = "Second AZ: subnets only, for the RDS subnet group and a future load balancer"
  type        = string
  default     = "ap-south-1b"
}

variable "app_port" {
  description = "Port the edge box proxies to on the app box (the frontend)"
  type        = number
  default     = 3000
}

variable "kms_key_arn" {
  description = "The environment's customer-managed KMS key (CR-4)"
  type        = string
}

variable "flow_log_retention_days" {
  description = "Days flow logs are kept. CERT-In requires at least 180."
  type        = number
  default     = 180

  validation {
    condition     = var.flow_log_retention_days >= 180
    error_message = "Flow logs must be kept at least 180 days (CERT-In directions, 2022)."
  }
}

variable "extra_allowed_domains" {
  description = "Domains the VPC may resolve on top of the built-in allowlist (e.g. an email or SMS provider)"
  type        = list(string)
  default     = []
}
