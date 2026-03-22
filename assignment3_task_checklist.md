# CS6650 Assignment 3 Development Task Checklist

## 1. Working Rules

- Use `Mockito` as the standard unit testing framework for mocked dependencies.
- Treat `Publisher Confirms`, `Manual Acknowledgements`, `ALB path-based routing`, and `ATW demonstration` as hard requirements, not optional enhancements.
- Prioritize local correctness before AWS deployment to save learner credits.
- Keep all services, RabbitMQ, and Warehouse as separate containers.

## 2. Project Setup Tasks

### 2.1 Repository Structure

- [ ] Create a clear multi-module or multi-service project structure.
- [ ] Add separate directories/modules for:
  - [ ] `product-service`
  - [ ] `shopping-cart-service`
  - [ ] `credit-card-authorizer`
  - [ ] `warehouse-consumer`
  - [ ] `load-test-client`
  - [ ] `infra` or `terraform`
  - [ ] `docker`
- [ ] Add a root `README`.
- [ ] Add a shared document for API/message contracts if needed.

### 2.2 Build and Dependency Setup

- [ ] Choose and standardize the Java version.
- [ ] Set up Maven or Gradle consistently across all services.
- [ ] Add unit test dependencies:
  - [ ] `JUnit 5`
  - [ ] `Mockito`
- [ ] Add service runtime dependencies:
  - [ ] web framework
  - [ ] JSON serialization
  - [ ] RabbitMQ client
  - [ ] validation library
- [ ] Add Docker build files for each service.

## 3. API and Contract Tasks

### 3.1 REST Contract Review

- [ ] Confirm all endpoints from `improved_api.yaml`.
- [ ] Confirm request/response bodies for:
  - [ ] product create
  - [ ] product get
  - [ ] shopping cart create
  - [ ] add item
  - [ ] checkout
  - [ ] credit card authorize
- [ ] Define validation and error response behavior.

### 3.2 RabbitMQ Message Contract

- [ ] Define the message structure sent from ShoppingCart to Warehouse.
- [ ] Include enough data for Warehouse to update:
  - [ ] total order count
  - [ ] quantity per product ID
- [ ] Decide on serialization format, likely JSON.

## 4. Service Implementation Tasks

### 4.1 Product Service

- [ ] Implement `POST /product`.
- [ ] Implement `GET /products/{productId}`.
- [ ] Reuse or adapt Assignment 2 logic.
- [ ] Add validation and error handling.
- [ ] Add health endpoint if helpful for ALB health checks.

### 4.2 Bad Product Service Variant

- [ ] Implement a Product service variant that:
  - [ ] returns success for about 50% of requests
  - [ ] returns `503` for about 50% of requests
- [ ] Ensure health checks still return `200`.
- [ ] Keep behavior isolated so it is only used for ATW demonstration.

### 4.3 ShoppingCart Service

- [ ] Implement `POST /shopping-cart`.
- [ ] Implement `POST /shopping-carts/{shoppingCartId}/addItem`.
- [ ] Implement `POST /shopping-carts/{shoppingCartId}/checkout`.
- [ ] Validate cart existence and cart state.
- [ ] Validate product ID and quantity bounds.
- [ ] Implement order/cart persistence or an acceptable temporary store.
- [ ] Call Credit Card Authorizer over HTTP.
- [ ] Publish warehouse message to RabbitMQ on payment success.
- [ ] Wait for `Publisher Confirm` before returning checkout success.
- [ ] Return appropriate error response on payment decline.

### 4.4 Credit Card Authorizer Service

- [ ] Implement `POST /credit-card-authorizer/authorize`.
- [ ] Validate credit card syntax `dddd-dddd-dddd-dddd`.
- [ ] Return `400` for invalid format.
- [ ] Return `200` for roughly 90% of valid requests.
- [ ] Return `402` for roughly 10% of valid requests.
- [ ] Make randomness injectable/testable where possible.

### 4.5 Warehouse Consumer

- [ ] Implement RabbitMQ consumer in Java.
- [ ] Connect to broker remotely or locally by config.
- [ ] Consume checkout/shipment messages.
- [ ] Use `Manual Acknowledgements`.
- [ ] Record:
  - [ ] total number of orders
  - [ ] total quantity ordered per product ID
- [ ] Print total number of orders on shutdown.
- [ ] Make counters thread-safe.
- [ ] Make consumer multithreaded.

## 5. RabbitMQ Reliability Tasks

### 5.1 Producer Side

- [ ] Configure ShoppingCart publisher for `Publisher Confirms`.
- [ ] Fail the request if confirm is not received as expected.
- [ ] Decide whether to create channel per request or use a channel pool.
- [ ] Add configuration for exchange/queue/routing if needed.

### 5.2 Consumer Side

- [ ] Disable auto-ack.
- [ ] Only acknowledge after Warehouse records the message.
- [ ] Handle consumer processing failures safely.
- [ ] Configure sensible prefetch settings.

### 5.3 Queue Observability

- [ ] Enable RabbitMQ management console.
- [ ] Verify queue depth can be observed during load tests.
- [ ] Record producer and consumer rates during tuning.

## 6. Persistence and State Tasks

- [ ] Decide the minimum persistence layer for each service.
- [ ] Persist products.
- [ ] Persist shopping carts.
- [ ] Persist cart items.
- [ ] Persist orders or checkout state if needed.
- [ ] Keep Warehouse counters in thread-safe runtime structures.

## 7. Unit Test Tasks

### 7.1 Testing Standard

- [ ] Use `JUnit 5` for test runner/assertions.
- [ ] Use `Mockito` for mocked collaborators.
- [ ] Keep unit tests fast and independent from RabbitMQ/AWS.

### 7.2 Product Service Unit Tests

- [ ] Test successful product creation.
- [ ] Test product retrieval.
- [ ] Test invalid product input handling.
- [ ] Test error paths if repository/service logic fails.

### 7.3 ShoppingCart Service Unit Tests

- [ ] Mock Product lookup dependency if used.
- [ ] Mock Credit Card Authorizer client with `Mockito`.
- [ ] Mock RabbitMQ publisher wrapper with `Mockito`.
- [ ] Test cart creation.
- [ ] Test add item success.
- [ ] Test add item validation failures.
- [ ] Test checkout success when payment authorized and publish confirm succeeds.
- [ ] Test checkout decline flow when CCA returns `402`.
- [ ] Test checkout failure when publish confirm fails.

### 7.4 Credit Card Authorizer Unit Tests

- [ ] Test valid card format acceptance.
- [ ] Test invalid card format returns `400`.
- [ ] Test authorization/decline branching with controlled randomness.

### 7.5 Warehouse Unit Tests

- [ ] Mock message delivery wrapper if consumer logic is separated.
- [ ] Test order counter increment.
- [ ] Test per-product quantity aggregation.
- [ ] Test manual ack is triggered only after processing logic completes.
- [ ] Test thread-safe update logic where practical.

## 8. Integration Test Tasks

- [ ] Test ShoppingCart to CCA integration locally.
- [ ] Test ShoppingCart to RabbitMQ publish flow locally.
- [ ] Test Warehouse consumes published messages.
- [ ] Test end-to-end checkout success path.
- [ ] Test checkout decline path.
- [ ] Test invalid credit card path.

## 9. Containerization Tasks

- [ ] Build Dockerfile for Product Service.
- [ ] Build Dockerfile for ShoppingCart Service.
- [ ] Build Dockerfile for Credit Card Authorizer.
- [ ] Build Dockerfile for Warehouse consumer.
- [ ] Add RabbitMQ container with management console enabled.
- [ ] Create local Docker Compose setup if helpful.
- [ ] Verify all containers communicate correctly.

## 10. AWS Infrastructure Tasks

These can wait until learner credentials are available.

### 10.1 Base Infrastructure

- [ ] Create Terraform structure.
- [ ] Provision network/security resources.
- [ ] Provision compute resources for services.
- [ ] Provision or deploy RabbitMQ host/container.

### 10.2 Load Balancer

- [ ] Create Application Load Balancer.
- [ ] Create target group for Product Service.
- [ ] Create target group for ShoppingCart Service.
- [ ] Create target group for Credit Card Authorizer.
- [ ] Add listener rules for path-based routing.
- [ ] Confirm Warehouse is not behind the ALB.

### 10.3 ATW Demonstration

- [ ] Deploy two good Product instances.
- [ ] Deploy one bad Product instance.
- [ ] Configure health checks so bad instance remains eligible.
- [ ] Enable and verify Automatic Target Weights behavior.
- [ ] Capture screenshot showing reduced traffic to bad instance.

## 11. Load Testing Tasks

### 11.1 Client Implementation

- [ ] Implement AWS-deployable Java load test client.
- [ ] Configure client to send `200,000` checkout requests.
- [ ] Send requests to the ALB endpoint.
- [ ] Measure:
  - [ ] successful requests
  - [ ] unsuccessful requests
  - [ ] wall time
  - [ ] throughput

### 11.2 Tuning Experiments

- [ ] Run tests with different client thread counts.
- [ ] Run tests with different Warehouse consumer thread counts.
- [ ] Observe RabbitMQ queue size over time.
- [ ] Find a setup where queue growth stabilizes.
- [ ] Aim for near-zero or low plateau queue depth.

## 12. Report Tasks

- [ ] Add all team member names.
- [ ] Add repo URL.
- [ ] Add screenshot of ATW avoiding bad Product instance.
- [ ] Add screenshot of RabbitMQ queue size over time.
- [ ] Add screenshot of Warehouse final output showing total orders.
- [ ] Briefly explain:
  - [ ] traffic distribution results
  - [ ] whether routing changed over time
  - [ ] queue tuning outcome

## 13. Final Verification Checklist

- [ ] All required services exist.
- [ ] All required containers exist.
- [ ] ShoppingCart uses `Publisher Confirms`.
- [ ] Warehouse uses `Manual Acknowledgements`.
- [ ] CCA validates card syntax and returns 90/10 outcomes.
- [ ] Bad Product instance returns roughly 50% `503`.
- [ ] ALB path-based routing works.
- [ ] ATW behavior is demonstrated with screenshots.
- [ ] Load test runs from AWS.
- [ ] Warehouse prints total order count at shutdown.
- [ ] Repo is readable and organized.
- [ ] Report includes all required screenshots and metadata.

## 14. Recommended Immediate Next Tasks

- [ ] Decide Java framework and build tool.
- [ ] Create service/module skeletons.
- [ ] Implement CCA first because it is isolated and easy to test.
- [ ] Implement ShoppingCart checkout orchestration next.
- [ ] Implement RabbitMQ publish/consume path locally.
- [ ] Add Mockito-based unit tests as each service is built.
