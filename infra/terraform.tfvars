aws_region                     = "us-west-2"
availability_zones             = ["us-west-2a", "us-west-2b"]
ami_id                         = "ami-03caad32a158f72db"
ssh_key_name                   = null
existing_instance_profile_name = "LabInstanceProfile"
admin_cidr_blocks              = ["45.30.93.106/32"]

container_images = {
  product_service        = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/product-service:latest"
  shopping_cart_service  = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/shopping-cart-service:latest"
  credit_card_authorizer = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/credit-card-authorizer:latest"
  warehouse_consumer     = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/warehouse-consumer:latest"
  load_test_client       = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/load-test-client:latest"
  rabbitmq               = "rabbitmq:3.13-management"
}

load_test_enabled = false
provision_load_tester = false

cart_instance_count        = 2
shopping_cart_storage_mode = "dynamodb"
shopping_cart_table_name   = "commercehub-a3-cart-state"

load_test_args = [
  "--requests=200000",
  "--threads=200",
  "--product-id=1",
  "--quantity=1",
  "--credit-card-number=1234-5678-9012-3456"
]
