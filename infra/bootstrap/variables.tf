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
  # GitHub's immutable OIDC subject (repository setting `use_immutable_subject`): owner and
  # repository carry their numeric IDs, so a renamed, deleted or re-created repository with the
  # same name can't match these trusts. Check: gh api repos/<owner>/<repo>/actions/oidc/customization/sub
  description = "Repository part of the OIDC sub claim, as GitHub issues it: owner@id/name@id"
  type        = string
  default     = "abhis9102@54002060/sevacenter@1402065755"
}

variable "apply_environment" {
  description = "GitHub environment whose jobs may assume the apply role. It must require a reviewer and allow only the main branch."
  type        = string
  default     = "staging"
}
