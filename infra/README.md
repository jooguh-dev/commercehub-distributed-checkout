# Terraform Setup

This directory provisions the AWS skeleton for Assignment 3:

- VPC with two public subnets
- Internet gateway and public routing
- Security groups for ALB and EC2 services
- Application Load Balancer
- Product, Shopping Cart, and Credit Card Authorizer target groups
- Path-based listener rules
- EC2 instances for:
  - product good instance
  - product bad instance
  - shopping cart
  - credit card authorizer
  - RabbitMQ
  - warehouse consumer
  - load test client
- Optional ECR repositories for application images

## Before Apply

1. Copy `terraform.tfvars.example` to `terraform.tfvars`.
2. Fill in:
   - `ami_id`
   - `ssh_key_name`
   - `admin_cidr_blocks`
   - `container_images.*`
3. Push your service images to ECR.
4. Run:

```bash
terraform init
terraform plan
terraform apply
```

For a full step-by-step deployment flow, including ECR build and push commands, use [deploy.md](/Users/xzhuge/Downloads/CS-6650/assignment/project/infra/deploy.md).

## Notes

- `product_bad` uses the same product image but injects `PRODUCT_BAD_INSTANCE_ENABLED=true`.
- `warehouse` is intentionally not attached to the ALB.
- `load-test-client` is provisioned as a separate EC2 instance. By default it does not auto-run the test. Set `load_test_enabled=true` to start the load generator on boot.
- The product target group is configured for weighted random routing with anomaly mitigation to support the ATW demo requirement.
- RabbitMQ management is exposed only to `admin_cidr_blocks`.
- The warehouse queue is declared with a dead-letter exchange and a `warehouse-orders.dlq` companion queue for poison messages.
- In your current learner account on March 21, 2026, `us-west-2` has a recent Amazon Linux 2023 AMI available as `ami-03caad32a158f72db`.
- The current learner account does not have any EC2 key pairs yet, so `ssh_key_name = null` is valid if you are comfortable managing the stack without SSH.
