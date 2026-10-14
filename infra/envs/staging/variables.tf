variable "region" {
  description = "AWS region. Personal data stays in India (CR-2): only ap-south-1 is accepted."
  type        = string
  default     = "ap-south-1"

  validation {
    condition     = var.region == "ap-south-1"
    error_message = "CR-2: every resource holding personal data is in ap-south-1 (Mumbai)."
  }
}

variable "alert_email" {
  description = "Where budget alerts go. Not committed: set it in terraform.tfvars or TF_VAR_alert_email."
  type        = string
  sensitive   = true
}

variable "monthly_budget_usd" {
  # Credits left / days left on the account, rounded down: spending more runs out before the end.
  description = "Monthly spend that triggers alerts at 25/50/100% (and a forecast alert)"
  type        = number
  default     = 30
}

variable "extra_allowed_domains" {
  description = "Extra names the VPC may resolve (DNS Firewall), e.g. an email or SMS provider"
  type        = list(string)
  default     = []
}
