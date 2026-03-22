locals {
  common_tags = {
    Project    = var.project_name
    Assignment = "cs6650-assignment3"
    ManagedBy  = "terraform"
  }

  service_ports = {
    product             = 8081
    cart                = 8080
    cca                 = 8082
    rabbitmq            = 5672
    rabbitmq_management = 15672
  }

  ecr_repositories = {
    product-service        = "product_service"
    shopping-cart-service  = "shopping_cart_service"
    credit-card-authorizer = "credit_card_authorizer"
    warehouse-consumer     = "warehouse_consumer"
    load-test-client       = "load_test_client"
  }

  public_subnet_ids = [for subnet_key in sort(keys(aws_subnet.public)) : aws_subnet.public[subnet_key].id]
}
