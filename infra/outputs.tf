output "alb_dns_name" {
  description = "Public DNS name of the assignment ALB."
  value       = aws_lb.application.dns_name
}

output "rabbitmq_management_url" {
  description = "RabbitMQ management console URL."
  value       = "http://${aws_instance.rabbitmq.public_ip}:${local.service_ports.rabbitmq_management}"
}

output "instance_public_ips" {
  description = "Public IP addresses by instance role."
  value = {
    product_good           = aws_instance.product_good.public_ip
    product_bad            = aws_instance.product_bad.public_ip
    shopping_cart          = [for instance in aws_instance.shopping_cart : instance.public_ip]
    credit_card_authorizer = aws_instance.credit_card_authorizer.public_ip
    rabbitmq               = aws_instance.rabbitmq.public_ip
    warehouse              = aws_instance.warehouse.public_ip
    load_tester            = try(aws_instance.load_tester[0].public_ip, null)
  }
}

output "instance_private_ips" {
  description = "Private IP addresses used for service-to-service communication."
  value = {
    product_good           = aws_instance.product_good.private_ip
    product_bad            = aws_instance.product_bad.private_ip
    shopping_cart          = [for instance in aws_instance.shopping_cart : instance.private_ip]
    credit_card_authorizer = aws_instance.credit_card_authorizer.private_ip
    rabbitmq               = aws_instance.rabbitmq.private_ip
    warehouse              = aws_instance.warehouse.private_ip
    load_tester            = try(aws_instance.load_tester[0].private_ip, null)
  }
}

output "shopping_cart_dynamodb_table_name" {
  description = "DynamoDB table backing shopping cart shared state."
  value       = aws_dynamodb_table.shopping_cart_state.name
}

output "ecr_repository_urls" {
  description = "ECR repository URLs when repository creation is enabled."
  value = {
    for name, repository in aws_ecr_repository.service_images : name => repository.repository_url
  }
}
