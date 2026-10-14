output "vpc_id" {
  value = aws_vpc.this.id
}

output "subnet_ids" {
  description = "Subnet IDs by tier: [workload AZ, second AZ]"
  value = {
    for tier in keys(local.subnets) : tier => [aws_subnet.this["${tier}-0"].id, aws_subnet.this["${tier}-1"].id]
  }
}

output "app_route_table_id" {
  description = "Gets the default route to the edge box in the compute round"
  value       = aws_route_table.app.id
}

output "security_group_ids" {
  value = {
    edge = aws_security_group.edge.id
    app  = aws_security_group.app.id
    db   = aws_security_group.db.id
  }
}
