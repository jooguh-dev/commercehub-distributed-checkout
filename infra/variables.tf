variable "project_name" {
  description = "Prefix used for AWS resource names."
  type        = string
  default     = "commercehub-a3"
}

variable "aws_region" {
  description = "AWS region for deployment."
  type        = string
  default     = "us-west-2"
}

variable "availability_zones" {
  description = "Two availability zones for public subnets."
  type        = list(string)
  default     = ["us-west-2a", "us-west-2b"]
}

variable "vpc_cidr" {
  description = "CIDR block for the assignment VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for the public subnets."
  type        = list(string)
  default     = ["10.42.1.0/24", "10.42.2.0/24"]
}

variable "admin_cidr_blocks" {
  description = "CIDR blocks allowed to SSH and access RabbitMQ management."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "ssh_key_name" {
  description = "Existing EC2 key pair name for SSH access. Leave null to skip key attachment."
  type        = string
  default     = null
}

variable "existing_instance_profile_name" {
  description = "Pre-created EC2 instance profile name available in the learner account."
  type        = string
  default     = "LabInstanceProfile"
}

variable "ami_id" {
  description = "AMI ID for Amazon Linux 2023 or another Docker-capable base image."
  type        = string
}

variable "instance_types" {
  description = "EC2 instance types per role."
  type = object({
    product     = string
    cart        = string
    cca         = string
    rabbitmq    = string
    warehouse   = string
    load_tester = string
  })
  default = {
    product     = "t3.small"
    cart        = "t3.small"
    cca         = "t3.small"
    rabbitmq    = "t3.small"
    warehouse   = "t3.small"
    load_tester = "t3.medium"
  }
}

variable "cart_instance_count" {
  description = "Number of shopping cart instances behind the ALB."
  type        = number
  default     = 1
}

variable "create_ecr_repositories" {
  description = "Whether Terraform should create ECR repositories for the app images."
  type        = bool
  default     = true
}

variable "container_images" {
  description = "Container image URIs. Replace placeholder values with ECR image tags before apply."
  type = object({
    product_service        = string
    shopping_cart_service  = string
    credit_card_authorizer = string
    warehouse_consumer     = string
    load_test_client       = string
    rabbitmq               = string
  })
  default = {
    product_service        = "REPLACE_ME_PRODUCT_IMAGE"
    shopping_cart_service  = "REPLACE_ME_CART_IMAGE"
    credit_card_authorizer = "REPLACE_ME_CCA_IMAGE"
    warehouse_consumer     = "REPLACE_ME_WAREHOUSE_IMAGE"
    load_test_client       = "REPLACE_ME_LOAD_TEST_IMAGE"
    rabbitmq               = "rabbitmq:3.13-management"
  }
}

variable "rabbitmq_queue_name" {
  description = "RabbitMQ queue used by the warehouse workflow."
  type        = string
  default     = "warehouse-orders"
}

variable "warehouse_consumer_threads" {
  description = "Number of warehouse consumer threads."
  type        = number
  default     = 4
}

variable "rabbitmq_prefetch_count" {
  description = "Prefetch count for each warehouse consumer channel."
  type        = number
  default     = 100
}

variable "rabbitmq_publish_timeout_ms" {
  description = "Publisher confirm timeout for Shopping Cart service."
  type        = number
  default     = 5000
}

variable "shopping_cart_storage_mode" {
  description = "Shopping cart persistence backend."
  type        = string
  default     = "dynamodb"
}

variable "shopping_cart_table_name" {
  description = "DynamoDB table name for shopping cart state and ID sequences."
  type        = string
  default     = "commercehub-a3-cart-state"
}

variable "load_test_enabled" {
  description = "Whether the load-test instance should auto-run the load test container at boot."
  type        = bool
  default     = false
}

variable "provision_load_tester" {
  description = "Whether to provision the dedicated load-test EC2 instance."
  type        = bool
  default     = false
}

variable "load_test_args" {
  description = "Arguments passed to the load test client when auto-run is enabled."
  type        = list(string)
  default = [
    "--requests=200000",
    "--threads=200",
    "--product-id=1",
    "--quantity=1",
    "--credit-card-number=1234-5678-9012-3456"
  ]
}
