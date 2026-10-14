output "edge_public_ip" {
  description = "Point the staging DNS records (both base domains and their wildcards) here"
  value       = aws_eip.edge.public_ip
}

output "app_instance_id" {
  value = aws_instance.app.id
}

output "edge_instance_id" {
  value = aws_instance.edge.id
}

output "db_identifier" {
  value = aws_db_instance.this.identifier
}
