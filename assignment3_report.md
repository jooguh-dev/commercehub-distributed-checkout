# CS6650 Assignment 3 Report

## Team Information

- Team members: `<fill in names>`
- Repository URL: `<fill in repo URL>`

## System Overview

This project extends the Assignment 2 Product Service into a multi-service e-commerce workflow deployed on AWS. The system includes:

- Product Service
- Shopping Cart Service
- Credit Card Authorizer Service
- RabbitMQ broker
- Warehouse consumer
- AWS Application Load Balancer with path-based routing
- Bad Product instance for Automatic Target Weights demonstration
- AWS-based load test client

The deployed HTTP entry point is:

- ALB DNS: `http://commercehub-a3-alb-117064485.us-west-2.elb.amazonaws.com`

The high-level request flow is:

1. Client creates a shopping cart.
2. Client adds an item to the cart.
3. Client calls checkout.
4. Shopping Cart Service synchronously calls Credit Card Authorizer.
5. If payment is approved, Shopping Cart Service publishes an order message to RabbitMQ and waits for publisher confirm.
6. Warehouse asynchronously consumes the message, manually acknowledges it, and updates warehouse statistics.

## Service Implementation

### Product Service

Implemented endpoints:

- `POST /product`
- `GET /products/{productId}`

The Product Service supports two deployment modes:

- normal mode
- bad-instance mode that returns `503` for approximately 50% of product read requests while still passing health checks

This bad instance is used for the ALB Automatic Target Weights demonstration.

### Shopping Cart Service

Implemented endpoints:

- `POST /shopping-cart`
- `POST /shopping-carts/{shoppingCartId}/addItem`
- `POST /shopping-carts/{shoppingCartId}/checkout`

Important implementation details:

- shopping cart state is stored in DynamoDB so the service can run behind multiple ALB targets
- optimistic locking was added to prevent silent overwrite of concurrent cart updates
- checkout uses an intermediate `CHECKOUT_IN_PROGRESS` state to reduce duplicate processing
- timed recovery was added so carts do not remain stuck forever if an instance fails mid-checkout
- RabbitMQ publishing uses connection reuse and thread-local channel reuse
- publisher retries are paired with warehouse-side idempotency

### Credit Card Authorizer

Implemented endpoint:

- `POST /credit-card-authorizer/authorize`

Behavior:

- invalid card format returns `400`
- valid card format returns `200` about 90% of the time
- valid card format returns `402` about 10% of the time

This explains why load tests show a success rate close to 90%.

### RabbitMQ and Warehouse

RabbitMQ is used as the asynchronous bridge between checkout and warehouse processing.

Reliability features implemented:

- Shopping Cart Service uses publisher confirms
- Warehouse uses manual acknowledgements
- Warehouse is multithreaded
- Warehouse statistics are thread-safe
- Warehouse deduplicates by `orderId` so retry-related duplicate publishes are not double-counted
- bad messages are routed to a dead-letter queue instead of being retried forever

Warehouse tracks:

- total number of processed orders
- total ordered quantity per product ID

On shutdown, Warehouse prints the total processed order count.

## AWS Deployment

The system was deployed to AWS in `us-west-2` using Terraform.

Infrastructure components:

- VPC with public subnets
- EC2 instances for Product, Shopping Cart, Credit Card Authorizer, RabbitMQ, Warehouse, and load-test client
- Application Load Balancer
- separate target groups for Product, Shopping Cart, and Credit Card Authorizer
- listener rules with path-based routing
- DynamoDB table for shared shopping cart state

Routing design:

- `/product` and `/products/*` route to Product target group
- `/shopping-cart` and `/shopping-carts/*` route to Shopping Cart target group
- `/credit-card-authorizer/*` route to Credit Card Authorizer target group

Warehouse is not behind the ALB because it consumes RabbitMQ messages directly.

## ALB and Automatic Target Weights

The Product target group includes:

- healthy Product instance
- bad Product instance

The bad Product instance intentionally returns `503` on about 50% of requests, allowing AWS ALB Automatic Target Weights to detect anomalous behavior and reduce traffic to the bad target over time.

What to include in the final PDF:

- screenshot of Product target group showing good and bad instances
- screenshot showing Automatic Target Weights / anomaly mitigation behavior
- brief explanation that health checks still stay green while real traffic error rates diverge

## RabbitMQ Reliability Validation

The RabbitMQ requirements from the assignment were implemented and validated.

### Publisher Confirm

Checkout success is only returned after RabbitMQ confirms broker receipt of the published message.

### Manual Ack

Warehouse only acknowledges a message after it has been successfully processed and counted.

### Dead-Letter Queue

Poison-message handling was added:

- invalid warehouse messages are rejected instead of endlessly requeued
- a dead-letter queue `warehouse-orders.dlq` is configured
- cloud validation confirmed that malformed messages leave the main queue and arrive in the DLQ

What to include in the final PDF:

- RabbitMQ management console screenshot during load test showing queue metrics
- RabbitMQ DLQ screenshot after malformed message injection, if you want to document the extra reliability work

## Testing

### Unit Testing

The project uses:

- JUnit 5
- Mockito

Unit tests cover:

- product creation and retrieval
- credit-card validation and authorization branching
- shopping-cart checkout orchestration
- optimistic-locking behavior
- warehouse statistics aggregation
- RabbitMQ publisher behavior

### Integration Testing

Implemented integration coverage includes:

- Shopping Cart to Credit Card Authorizer flow
- Shopping Cart to RabbitMQ publish flow
- Warehouse consumer processing
- end-to-end checkout flow
- dead-letter queue validation for malformed messages

Note:

- the DLQ integration test uses Testcontainers and will skip automatically if Docker socket access is unavailable in the local environment

## Performance Investigation and Optimization

Several rounds of performance work were completed.

### Initial Bottleneck

The first major bottleneck was the Shopping Cart checkout path. Measured phase timings showed that checkout latency dominated request cost, while RabbitMQ queue depth stayed nearly flat.

### Improvements Applied

Optimizations included:

- Apache HttpClient connection pooling for the Shopping Cart to Credit Card Authorizer call
- RabbitMQ connection reuse
- per-thread RabbitMQ channel reuse
- retry, circuit breaker, and bulkhead protections around MQ publishing
- warehouse idempotency based on `orderId`
- shared DynamoDB cart storage to support multiple Shopping Cart instances
- optimistic locking and in-progress checkout state management
- conversion of `orderId` to UUID
- conversion of `shoppingCartId` to UUID to remove sequence-key hotspot

### Strong Result from the Fast Cart Path

The strongest measured load-test result came from the optimized single-cart configuration before DynamoDB-backed horizontal scaling changed the tradeoff:

- requests: `200,000`
- threads: `500`
- successes: `179,922`
- failures: `20,078`
- success rate: `89.96%`
- throughput: `1577.60 req/s`
- average checkout latency: `118.57 ms`
- average successful end-to-end scenario latency: `315.61 ms`

Resource snapshot during that run:

- Shopping Cart CPU: about `77.89%`
- Credit Card Authorizer CPU: about `28.64%`
- RabbitMQ CPU: about `33.91%`

RabbitMQ behavior during the run:

- publish rate about `1457.8/s`
- ack rate about `1449.4/s`
- queue stayed near empty
- end state returned to `0` ready and `0` unacknowledged messages

Interpretation:

- the system successfully handled `200,000` checkout requests
- the observed failure rate remained consistent with the assignment’s 10% credit-card decline behavior
- RabbitMQ and Warehouse were not the bottleneck in that configuration

### Horizontal Scaling Tradeoff

After moving Shopping Cart state to DynamoDB and enabling two Shopping Cart instances, correctness and scale-out support improved, but the request path became heavier because create, addItem, and checkout each required shared storage operations.

Observed investigation results:

- DynamoDB throttling was not detected
- `ReadThrottleEvents` and `WriteThrottleEvents` were zero during the investigated window
- optimistic-locking conflicts were not elevated
- the main cost came from additional synchronous DynamoDB reads and writes

This was a useful tradeoff study:

- single-instance in-memory cart achieved the highest throughput
- DynamoDB-backed shared cart storage enabled correct horizontal scaling
- the horizontally scalable version favored correctness and elasticity over raw peak throughput

## Evidence Checklist for Final PDF

The following evidence should be included in the submitted PDF:

1. Team member names
2. Git repository URL
3. Screenshot of ALB target groups and listener rules
4. Screenshot showing good Product target and bad Product target
5. Screenshot showing ATW behavior shifting traffic away from the bad Product instance
6. RabbitMQ management console screenshot showing queue length / publish / ack activity during load test
7. Screenshot of Warehouse shutdown log printing total processed orders
8. Optional screenshot of DLQ after malformed message injection

## Lessons Learned

This assignment highlighted several core distributed-systems tradeoffs:

- correctness and horizontal scalability often add latency and reduce raw throughput
- publisher confirms and manual acknowledgements improve reliability but require careful performance tuning
- ALB path-based routing and anomaly mitigation are straightforward to deploy but require deliberate failure-injection design
- shared-state services should not be horizontally scaled until state storage is externalized
- retry logic must be paired with idempotency to avoid duplicate side effects

## Conclusion

The final system satisfies the main technical goals of the assignment:

- multi-service cloud deployment on AWS
- ALB path-based routing
- RabbitMQ publisher confirms
- RabbitMQ manual acknowledgements
- bad Product instance for ATW demonstration
- Warehouse statistics tracking
- successful large-scale checkout load testing

The system was also extended beyond the minimum baseline with:

- optimistic locking
- checkout recovery timeout
- dead-letter queue handling
- warehouse idempotency
- shared cart persistence for multi-instance Shopping Cart deployment

## Appendix: Recommended Captions

### ATW Screenshot Caption

`Figure X. Product target group with one healthy normal instance and one healthy bad instance. Under live traffic, ALB anomaly mitigation reduces traffic sent to the bad instance because it returns elevated 503 responses.`

### RabbitMQ Screenshot Caption

`Figure X. RabbitMQ management console during checkout load test. Publish and acknowledgement rates remain close, and queue depth stays low, showing Warehouse can keep up with Shopping Cart message production.`

### Warehouse Shutdown Screenshot Caption

`Figure X. Warehouse consumer shutdown output showing total processed orders, which satisfies the assignment requirement to print total orders when the consumer stops.`
