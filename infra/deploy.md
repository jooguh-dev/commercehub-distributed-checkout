# AWS Deploy Runbook

This runbook takes the project from local source code to a deployed AWS Assignment 3 stack.

## 1. Export Temporary AWS Credentials

Use your current learner credentials in the shell for this session only:

```bash
export AWS_ACCESS_KEY_ID='REPLACE_ME'
export AWS_SECRET_ACCESS_KEY='REPLACE_ME'
export AWS_SESSION_TOKEN='REPLACE_ME'
export AWS_DEFAULT_REGION='us-west-2'
```

Verify the session:

```bash
aws sts get-caller-identity
```

## 2. Create ECR Repositories with Terraform

Initialize Terraform:

```bash
terraform -chdir=infra init
```

Create a working tfvars file:

```bash
cp infra/terraform.tfvars.example infra/terraform.tfvars
```

At this stage you can keep placeholder image values. The goal is to let Terraform create the ECR repositories first:

```bash
terraform -chdir=infra apply \
  -target=aws_ecr_repository.service_images
```

Get the ECR repository URLs:

```bash
terraform -chdir=infra output ecr_repository_urls
```

For this project the repository names will be:

- `${project_name}/product-service`
- `${project_name}/shopping-cart-service`
- `${project_name}/credit-card-authorizer`
- `${project_name}/warehouse-consumer`
- `${project_name}/load-test-client`

## 3. Log In to ECR

```bash
aws ecr get-login-password --region us-west-2 | \
docker login --username AWS --password-stdin 322362397412.dkr.ecr.us-west-2.amazonaws.com
```

## 4. Build the Service Images

Build from the repository root:

```bash
docker build -f docker/product-service.Dockerfile -t product-service:latest .
docker build -f docker/shopping-cart-service.Dockerfile -t shopping-cart-service:latest .
docker build -f docker/credit-card-authorizer.Dockerfile -t credit-card-authorizer:latest .
docker build -f docker/warehouse-consumer.Dockerfile -t warehouse-consumer:latest .
docker build -f docker/load-test-client.Dockerfile -t load-test-client:latest .
```

## 5. Tag and Push Images

Set the registry prefix once:

```bash
export ECR_PREFIX='322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3'
```

Tag:

```bash
docker tag product-service:latest ${ECR_PREFIX}/product-service:latest
docker tag shopping-cart-service:latest ${ECR_PREFIX}/shopping-cart-service:latest
docker tag credit-card-authorizer:latest ${ECR_PREFIX}/credit-card-authorizer:latest
docker tag warehouse-consumer:latest ${ECR_PREFIX}/warehouse-consumer:latest
docker tag load-test-client:latest ${ECR_PREFIX}/load-test-client:latest
```

Push:

```bash
docker push ${ECR_PREFIX}/product-service:latest
docker push ${ECR_PREFIX}/shopping-cart-service:latest
docker push ${ECR_PREFIX}/credit-card-authorizer:latest
docker push ${ECR_PREFIX}/warehouse-consumer:latest
docker push ${ECR_PREFIX}/load-test-client:latest
```

## 6. Fill In `infra/terraform.tfvars`

Use these current learner values as a starting point:

```hcl
aws_region         = "us-west-2"
availability_zones = ["us-west-2a", "us-west-2b"]
ami_id             = "ami-03caad32a158f72db"
ssh_key_name       = null
admin_cidr_blocks  = ["YOUR_IP/32"]

container_images = {
  product_service         = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/product-service:latest"
  shopping_cart_service   = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/shopping-cart-service:latest"
  credit_card_authorizer  = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/credit-card-authorizer:latest"
  warehouse_consumer      = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/warehouse-consumer:latest"
  load_test_client        = "322362397412.dkr.ecr.us-west-2.amazonaws.com/commercehub-a3/load-test-client:latest"
  rabbitmq                = "rabbitmq:3.13-management"
}
```

Replace `YOUR_IP/32` with your real public IP:

```bash
curl ifconfig.me
```

## 7. Deploy the Full Stack

Preview:

```bash
terraform -chdir=infra plan
```

Apply:

```bash
terraform -chdir=infra apply
```

## 8. Verify the AWS Stack

Get the ALB DNS name:

```bash
terraform -chdir=infra output alb_dns_name
```

Get the RabbitMQ management URL:

```bash
terraform -chdir=infra output rabbitmq_management_url
```

Basic checks:

```bash
curl http://$(terraform -chdir=infra output -raw alb_dns_name)/product
curl http://$(terraform -chdir=infra output -raw alb_dns_name)/shopping-cart
curl http://$(terraform -chdir=infra output -raw alb_dns_name)/credit-card-authorizer/health
```

## 9. Run the Official Load Test

By default the load-test instance is created but does not auto-run.

To let the instance launch the test at boot, set:

```hcl
load_test_enabled = true
```

You can also tune:

```hcl
load_test_args = [
  "--requests=200000",
  "--threads=200",
  "--product-id=1",
  "--quantity=1",
  "--credit-card-number=1234-5678-9012-3456"
]
```

Then re-apply:

```bash
terraform -chdir=infra apply
```

## 10. Evidence to Capture for the Report

- ALB target group state for Product, including the bad instance
- ATW behavior showing traffic moving away from the bad Product instance
- RabbitMQ queue depth during the load test
- Warehouse shutdown summary showing total orders processed
- Repository URL and deployment screenshots

## Notes

- `Warehouse` is not behind the ALB by design.
- `product-bad` uses the same application image as `product-good` and injects `PRODUCT_BAD_INSTANCE_ENABLED=true`.
- If learner credits are tight, create the ECR repositories first, push images, and only then deploy the EC2 and ALB resources.
