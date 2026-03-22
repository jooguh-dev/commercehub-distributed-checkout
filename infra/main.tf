resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-vpc"
  })
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-igw"
  })
}

resource "aws_subnet" "public" {
  for_each = {
    for index, cidr in var.public_subnet_cidrs : index => {
      cidr_block        = cidr
      availability_zone = var.availability_zones[index]
    }
  }

  vpc_id                  = aws_vpc.main.id
  cidr_block              = each.value.cidr_block
  availability_zone       = each.value.availability_zone
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-public-${each.key + 1}"
  })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-public-rt"
  })
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb-sg"
  description = "Public access to the assignment ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-alb-sg"
  })
}

resource "aws_security_group" "services" {
  name        = "${var.project_name}-services-sg"
  description = "Service-to-service and admin access for EC2 hosts"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Product traffic from ALB"
    from_port       = local.service_ports.product
    to_port         = local.service_ports.product
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description     = "Shopping cart traffic from ALB"
    from_port       = local.service_ports.cart
    to_port         = local.service_ports.cart
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description     = "CCA traffic from ALB"
    from_port       = local.service_ports.cca
    to_port         = local.service_ports.cca
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description = "Service-to-service within the VPC"
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = [aws_vpc.main.cidr_block]
  }

  ingress {
    description = "RabbitMQ management"
    from_port   = local.service_ports.rabbitmq_management
    to_port     = local.service_ports.rabbitmq_management
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr_blocks
  }

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-services-sg"
  })
}

resource "aws_ecr_repository" "service_images" {
  for_each = var.create_ecr_repositories ? local.ecr_repositories : {}

  name                 = "${var.project_name}/${each.key}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${each.key}"
  })
}

resource "aws_dynamodb_table" "shopping_cart_state" {
  name         = var.shopping_cart_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"

  attribute {
    name = "pk"
    type = "S"
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-shopping-cart-state"
  })
}

resource "aws_lb" "application" {
  name               = "${var.project_name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [for subnet in aws_subnet.public : subnet.id]

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-alb"
  })
}

resource "aws_lb_target_group" "product" {
  name                              = "${var.project_name}-product"
  port                              = local.service_ports.product
  protocol                          = "HTTP"
  vpc_id                            = aws_vpc.main.id
  target_type                       = "instance"
  load_balancing_algorithm_type     = "weighted_random"
  load_balancing_anomaly_mitigation = "on"

  health_check {
    enabled             = true
    path                = "/health"
    protocol            = "HTTP"
    port                = "traffic-port"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 15
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-product-tg"
  })
}

resource "aws_lb_target_group" "cart" {
  name        = "${var.project_name}-cart"
  port        = local.service_ports.cart
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "instance"

  health_check {
    enabled             = true
    path                = "/health"
    protocol            = "HTTP"
    port                = "traffic-port"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 15
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-cart-tg"
  })
}

resource "aws_lb_target_group" "cca" {
  name        = "${var.project_name}-cca"
  port        = local.service_ports.cca
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "instance"

  health_check {
    enabled             = true
    path                = "/health"
    protocol            = "HTTP"
    port                = "traffic-port"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 5
    interval            = 15
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-cca-tg"
  })
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.application.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = "{\"error\":\"NO_ROUTE\",\"message\":\"No listener rule matched the request path.\"}"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "product" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.product.arn
  }

  condition {
    path_pattern {
      values = ["/product", "/products/*"]
    }
  }
}

resource "aws_lb_listener_rule" "cart" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 20

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.cart.arn
  }

  condition {
    path_pattern {
      values = ["/shopping-cart", "/shopping-carts/*"]
    }
  }
}

resource "aws_lb_listener_rule" "cca" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 30

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.cca.arn
  }

  condition {
    path_pattern {
      values = ["/credit-card-authorizer/*"]
    }
  }
}

resource "aws_instance" "product_good" {
  ami                         = var.ami_id
  instance_type               = var.instance_types.product
  subnet_id                   = aws_subnet.public["0"].id
  vpc_security_group_ids      = [aws_security_group.services.id]
  iam_instance_profile        = var.existing_instance_profile_name
  key_name                    = var.ssh_key_name
  associate_public_ip_address = true
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/templates/docker-host-user-data.sh.tftpl", {
    container_name    = "product-service"
    image_uri         = var.container_images.product_service
    host_port         = local.service_ports.product
    container_port    = local.service_ports.product
    additional_ports  = []
    perform_ecr_login = true
    aws_region        = var.aws_region
    ecr_registry      = split("/", var.container_images.product_service)[0]
    env_lines         = []
  })

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-product-good"
    Role = "product-good"
  })
}

resource "aws_instance" "product_bad" {
  ami                         = var.ami_id
  instance_type               = var.instance_types.product
  subnet_id                   = aws_subnet.public["1"].id
  vpc_security_group_ids      = [aws_security_group.services.id]
  iam_instance_profile        = var.existing_instance_profile_name
  key_name                    = var.ssh_key_name
  associate_public_ip_address = true
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/templates/docker-host-user-data.sh.tftpl", {
    container_name    = "product-service-bad"
    image_uri         = var.container_images.product_service
    host_port         = local.service_ports.product
    container_port    = local.service_ports.product
    additional_ports  = []
    perform_ecr_login = true
    aws_region        = var.aws_region
    ecr_registry      = split("/", var.container_images.product_service)[0]
    env_lines = [
      "PRODUCT_BAD_INSTANCE_ENABLED=true"
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-product-bad"
    Role = "product-bad"
  })
}

resource "aws_instance" "credit_card_authorizer" {
  ami                         = var.ami_id
  instance_type               = var.instance_types.cca
  subnet_id                   = aws_subnet.public["0"].id
  vpc_security_group_ids      = [aws_security_group.services.id]
  iam_instance_profile        = var.existing_instance_profile_name
  key_name                    = var.ssh_key_name
  associate_public_ip_address = true
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/templates/docker-host-user-data.sh.tftpl", {
    container_name    = "credit-card-authorizer"
    image_uri         = var.container_images.credit_card_authorizer
    host_port         = local.service_ports.cca
    container_port    = local.service_ports.cca
    additional_ports  = []
    perform_ecr_login = true
    aws_region        = var.aws_region
    ecr_registry      = split("/", var.container_images.credit_card_authorizer)[0]
    env_lines         = []
  })

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-cca"
    Role = "credit-card-authorizer"
  })
}

resource "aws_instance" "rabbitmq" {
  ami                         = var.ami_id
  instance_type               = var.instance_types.rabbitmq
  subnet_id                   = aws_subnet.public["0"].id
  vpc_security_group_ids      = [aws_security_group.services.id]
  iam_instance_profile        = var.existing_instance_profile_name
  key_name                    = var.ssh_key_name
  associate_public_ip_address = true
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/templates/docker-host-user-data.sh.tftpl", {
    container_name    = "rabbitmq"
    image_uri         = var.container_images.rabbitmq
    host_port         = local.service_ports.rabbitmq
    container_port    = local.service_ports.rabbitmq
    perform_ecr_login = false
    aws_region        = var.aws_region
    ecr_registry      = ""
    additional_ports = [
      {
        host      = local.service_ports.rabbitmq_management
        container = local.service_ports.rabbitmq_management
      }
    ]
    env_lines = [
      "RABBITMQ_DEFAULT_USER=guest",
      "RABBITMQ_DEFAULT_PASS=guest"
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-rabbitmq"
    Role = "rabbitmq"
  })
}

resource "aws_instance" "warehouse" {
  ami                         = var.ami_id
  instance_type               = var.instance_types.warehouse
  subnet_id                   = aws_subnet.public["1"].id
  vpc_security_group_ids      = [aws_security_group.services.id]
  iam_instance_profile        = var.existing_instance_profile_name
  key_name                    = var.ssh_key_name
  associate_public_ip_address = true
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/templates/docker-host-user-data.sh.tftpl", {
    container_name    = "warehouse-consumer"
    image_uri         = var.container_images.warehouse_consumer
    host_port         = 18083
    container_port    = 18083
    additional_ports  = []
    perform_ecr_login = true
    aws_region        = var.aws_region
    ecr_registry      = split("/", var.container_images.warehouse_consumer)[0]
    env_lines = [
      "RABBITMQ_HOST=${aws_instance.rabbitmq.private_ip}",
      "RABBITMQ_PORT=${local.service_ports.rabbitmq}",
      "RABBITMQ_USERNAME=guest",
      "RABBITMQ_PASSWORD=guest",
      "RABBITMQ_VHOST=/",
      "RABBITMQ_QUEUE=${var.rabbitmq_queue_name}",
      "RABBITMQ_DEAD_LETTER_EXCHANGE=${var.rabbitmq_queue_name}.dlx",
      "RABBITMQ_DEAD_LETTER_QUEUE=${var.rabbitmq_queue_name}.dlq",
      "RABBITMQ_DEAD_LETTER_ROUTING_KEY=${var.rabbitmq_queue_name}",
      "RABBITMQ_PREFETCH_COUNT=${var.rabbitmq_prefetch_count}",
      "WAREHOUSE_CONSUMER_THREADS=${var.warehouse_consumer_threads}",
      "WAREHOUSE_REQUEUE_PROCESSING_FAILURES=true"
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-warehouse"
    Role = "warehouse"
  })
}

resource "aws_instance" "shopping_cart" {
  count                       = var.cart_instance_count
  ami                         = var.ami_id
  instance_type               = var.instance_types.cart
  subnet_id                   = local.public_subnet_ids[count.index % length(local.public_subnet_ids)]
  vpc_security_group_ids      = [aws_security_group.services.id]
  iam_instance_profile        = var.existing_instance_profile_name
  key_name                    = var.ssh_key_name
  associate_public_ip_address = true
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/templates/docker-host-user-data.sh.tftpl", {
    container_name    = "shopping-cart-service-${count.index + 1}"
    image_uri         = var.container_images.shopping_cart_service
    host_port         = local.service_ports.cart
    container_port    = local.service_ports.cart
    additional_ports  = []
    perform_ecr_login = true
    aws_region        = var.aws_region
    ecr_registry      = split("/", var.container_images.shopping_cart_service)[0]
    env_lines = [
      "SERVICES_CREDIT_CARD_AUTHORIZER_BASE_URL=http://${aws_instance.credit_card_authorizer.private_ip}:${local.service_ports.cca}",
      "CART_STORAGE_MODE=${var.shopping_cart_storage_mode}",
      "CART_STORAGE_DYNAMODB_TABLE=${aws_dynamodb_table.shopping_cart_state.name}",
      "CART_CHECKOUT_IN_PROGRESS_TIMEOUT_MS=30000",
      "RABBITMQ_HOST=${aws_instance.rabbitmq.private_ip}",
      "RABBITMQ_PORT=${local.service_ports.rabbitmq}",
      "RABBITMQ_USERNAME=guest",
      "RABBITMQ_PASSWORD=guest",
      "RABBITMQ_VIRTUAL_HOST=/",
      "RABBITMQ_QUEUE=${var.rabbitmq_queue_name}",
      "RABBITMQ_DEAD_LETTER_EXCHANGE=${var.rabbitmq_queue_name}.dlx",
      "RABBITMQ_DEAD_LETTER_QUEUE=${var.rabbitmq_queue_name}.dlq",
      "RABBITMQ_DEAD_LETTER_ROUTING_KEY=${var.rabbitmq_queue_name}",
      "RABBITMQ_PUBLISH_TIMEOUT_MS=${var.rabbitmq_publish_timeout_ms}"
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-cart-${count.index + 1}"
    Role = "shopping-cart"
  })
}

resource "aws_instance" "load_tester" {
  count                       = var.provision_load_tester ? 1 : 0
  ami                         = var.ami_id
  instance_type               = var.instance_types.load_tester
  subnet_id                   = aws_subnet.public["0"].id
  vpc_security_group_ids      = [aws_security_group.services.id]
  iam_instance_profile        = var.existing_instance_profile_name
  key_name                    = var.ssh_key_name
  associate_public_ip_address = true
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/templates/load-test-user-data.sh.tftpl", {
    image_uri         = var.container_images.load_test_client
    load_test_enabled = var.load_test_enabled
    command_args      = concat(["--base-url=http://${aws_lb.application.dns_name}"], var.load_test_args)
  })

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-load-test"
    Role = "load-test-client"
  })
}

resource "aws_lb_target_group_attachment" "product_good" {
  target_group_arn = aws_lb_target_group.product.arn
  target_id        = aws_instance.product_good.id
  port             = local.service_ports.product
}

resource "aws_lb_target_group_attachment" "product_bad" {
  target_group_arn = aws_lb_target_group.product.arn
  target_id        = aws_instance.product_bad.id
  port             = local.service_ports.product
}

resource "aws_lb_target_group_attachment" "cart" {
  count            = var.cart_instance_count
  target_group_arn = aws_lb_target_group.cart.arn
  target_id        = aws_instance.shopping_cart[count.index].id
  port             = local.service_ports.cart
}

resource "aws_lb_target_group_attachment" "cca" {
  target_group_arn = aws_lb_target_group.cca.arn
  target_id        = aws_instance.credit_card_authorizer.id
  port             = local.service_ports.cca
}
