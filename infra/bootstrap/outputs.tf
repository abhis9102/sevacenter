output "state_bucket" {
  description = "Pass to every stack's init: -backend-config=bucket=<this>"
  value       = aws_s3_bucket.state.bucket
}

output "plan_role_arn" {
  description = "GitHub secret TF_PLAN_ROLE_ARN"
  value       = aws_iam_role.plan.arn
}

output "apply_role_arn" {
  description = "GitHub environment secret TF_APPLY_ROLE_ARN (environment: staging)"
  value       = aws_iam_role.apply.arn
}

output "workload_boundary_arn" {
  description = "Every role the stacks create must set permissions_boundary to this"
  value       = aws_iam_policy.workload_boundary.arn
}

output "workload_iam_path" {
  description = "Every role, policy and instance profile the stacks create must use this path"
  value       = local.workload_path
}
