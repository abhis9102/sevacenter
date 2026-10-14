variable "region" {
  description = "Home region. Personal data stays in India (CR-2): only ap-south-1 is accepted."
  type        = string
  default     = "ap-south-1"

  validation {
    condition     = var.region == "ap-south-1"
    error_message = "CR-2: everything is in ap-south-1 (Mumbai)."
  }
}

variable "github_repository" {
  description = "owner/name of the repository whose workflows may assume the CI roles"
  type        = string
  default     = "abhis9102/sevacenter"
}

variable "apply_environment" {
  description = "GitHub environment whose jobs may assume the apply role. It must require a reviewer and allow only the main branch."
  type        = string
  default     = "staging"
}
