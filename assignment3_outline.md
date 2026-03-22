# CS6650 Assignment 3 Full Outline

## 1. Assignment Big Picture

Assignment 3 is a team-based systems integration project. The goal is not to build a complicated business domain, but to demonstrate that the team can design, deploy, and test a multi-service cloud system that combines:

- multiple microservices,
- AWS Application Load Balancing,
- a mock external dependency,
- RabbitMQ asynchronous messaging,
- reliability mechanisms in RabbitMQ,
- and load testing with system-level tuning.

Compared with Assignment 2, this assignment extends the existing `Product Service` into a larger e-commerce workflow centered around shopping cart checkout and asynchronous warehouse processing.

## 2. What the PDF Actually Requires

The assignment PDF requires the team to build and deploy:

1. the existing `Product Service` from Assignment 2,
2. a new `ShoppingCart Service`,
3. a new mock `Credit Card Authorizer` service,
4. a RabbitMQ broker,
5. a `Warehouse` consumer connected to RabbitMQ,
6. an AWS Application Load Balancer with path-based routing and target groups,
7. a special "bad" Product service instance to demonstrate Automatic Target Weights,
8. an AWS-based load test client that sends `200,000` checkout requests.

Important architectural constraint:

- `Product Service`, `ShoppingCart Service`, and `Credit Card Authorizer` are HTTP services behind the Application Load Balancer.
- `Warehouse` is not exposed behind the Load Balancer because it communicates through RabbitMQ.
- All new services, the RabbitMQ broker, and the Warehouse consumer must run as separate containers.

## 3. Required Business Workflow

The normal business flow described by the PDF and `Use_Cases.md` is:

1. Client creates a shopping cart via `POST /shopping-cart`.
2. Client adds one or more items to the cart via `addItem`.
3. Client calls shopping cart `checkout`, passing:
   - `shoppingCartID`
   - `credit_card_number`
4. `ShoppingCart Service` validates the request and calls `Credit Card Authorizer`.
5. If the card is declined:
   - stop processing,
   - return error to the client.
6. If the card is authorized:
   - `ShoppingCart Service` publishes a shipment/order message to RabbitMQ,
   - then returns success to the client after the broker confirms receipt.
7. `Warehouse` consumes the message asynchronously and updates internal counts.

Key concept:

- The checkout flow is synchronous until RabbitMQ confirms that it has received the message.
- The actual warehouse processing is asynchronous.
- The client does not wait for shipment completion.

## 4. Core Services and Responsibilities

### 4.1 Product Service

This is the service carried over from Assignment 2.

Responsibilities:

- create products,
- fetch product information,
- participate in ALB routing tests,
- support a "bad" variant for ATW demonstration.

Expected endpoints from the provided API:

- `POST /product`
- `GET /products/{productId}`

Special requirement for testing ALB behavior:

- create a "bad" version of Product Service that returns:
  - `503` for about 50% of requests,
  - success for about 50% of requests.

This bad service must remain in rotation so AWS ATW can shift traffic away from it.

### 4.2 ShoppingCart Service

This is the orchestration service for the order flow.

Responsibilities:

- create shopping carts,
- add items to carts,
- checkout a cart,
- call Credit Card Authorizer,
- publish warehouse messages to RabbitMQ,
- wait for Publisher Confirm before returning success.

Expected endpoints from the provided API:

- `POST /shopping-cart`
- `POST /shopping-carts/{shoppingCartId}/addItem`
- `POST /shopping-carts/{shoppingCartId}/checkout`

This service is the most important service in the assignment because it connects the synchronous HTTP world to the asynchronous RabbitMQ world.

### 4.3 Credit Card Authorizer Service

This is a mock external dependency.

Responsibilities:

- validate credit card string syntax,
- randomly authorize or decline requests.

Rules required by the assignment:

- valid format is exactly `dddd-dddd-dddd-dddd`
- invalid syntax returns `400`
- valid syntax returns:
  - `200` about 90% of the time
  - `402` about 10% of the time

This service simulates a real payment authorizer without requiring any real payment system.

### 4.4 RabbitMQ Broker

This is the messaging backbone between ShoppingCart and Warehouse.

Responsibilities:

- receive published order/shipment messages,
- confirm accepted messages to the publisher,
- hold messages until Warehouse consumers process them.

Required reliability feature:

- `Publisher Confirms` must be used by `ShoppingCart Service`.

### 4.5 Warehouse Consumer

This is a Java RabbitMQ client that acts as an asynchronous warehouse.

Responsibilities:

- consume shipment/order messages from RabbitMQ,
- manually acknowledge successfully processed messages,
- maintain:
  - total number of orders,
  - total quantity ordered for each product ID,
- print the total number of orders on shutdown.

Required behavior:

- must be multithreaded,
- shared state must be thread-safe,
- should consume messages fast enough to keep queue growth under control.

## 5. API and IDL View of the System

The provided material defines the system from several angles:

- `improved_api.yaml` describes REST endpoints and payloads,
- `idl/` defines service interfaces conceptually,
- `Use_Cases.md` describes business scenarios,
- sample RabbitMQ Java code demonstrates producer/consumer threading patterns.

For implementation purposes, the most important interfaces are:

- Product create/get APIs,
- Shopping cart create/add/checkout APIs,
- Credit card authorize API,
- RabbitMQ message contract between ShoppingCart and Warehouse.

## 6. RabbitMQ Requirements You Must Not Miss

This is one of the most important sections of the assignment.

### 6.1 Publisher Confirms

The `ShoppingCart Service` must use `Publisher Confirms`.

Meaning:

- SCS publishes the warehouse message,
- RabbitMQ confirms the broker received it,
- only then should SCS return success for checkout.

This is not optional in the PDF.

### 6.2 Manual Consumer Acknowledgements

The `Warehouse` consumer must use manual acknowledgements.

Meaning:

- Warehouse receives a message,
- records the relevant product/order info,
- explicitly acknowledges the message,
- RabbitMQ can then remove it from the queue.

### 6.3 Why This Matters

The assignment is testing whether the team understands the difference between:

- producer-side delivery confirmation,
- consumer-side processing acknowledgement.

The SCS must know the broker accepted the message.
The Warehouse must tell the broker it actually processed the message.

## 7. Load Balancer Requirements

The assignment explicitly requires an AWS `Application Load Balancer`.

### 7.1 Target Groups

There must be separate target groups for:

- `Product Service`
- `ShoppingCart Service`
- `CreditCardAuthorizer Service`

There is no target group for `Warehouse`.

### 7.2 Routing

The ALB must use path-based routing using listener rules.

Examples given by the PDF:

- paths containing `/product` route to Product target group,
- paths containing `/shopping-cart` route to ShoppingCart target group,
- paths containing `/credit-card` route to Credit Card target group.

### 7.3 Automatic Target Weights

Inside the Product target group, AWS ATW must be enabled and demonstrated.

You must deploy:

- two good Product instances,
- one bad Product instance that returns `503` 50% of the time.

The report must show that the ALB shifts traffic away from the bad instance over time.

### 7.4 Health Check Nuance

The PDF explicitly warns:

- do not let the bad instance be removed from service by health checks,
- instead configure health checks so the instance remains eligible,
- otherwise ATW cannot demonstrate traffic shifting.

This means health check behavior must be designed intentionally.

## 8. Load Testing Requirements

All load testing must happen on AWS, not only locally.

Requirements:

- load test client must also run in AWS,
- send `200,000` shopping cart checkout requests,
- send them to the Load Balancer,
- send them as fast as possible.

Important simplification:

- for the load test, you do not need to simulate cart creation or add-item calls,
- each load-test request can just be a checkout request.

Required final metrics:

- number of successful requests,
- number of unsuccessful requests,
- total wall-clock run time,
- total throughput in requests per second.

## 9. Queue Tuning Goal

The PDF is not only asking for throughput. It is also asking for a good producer/consumer balance.

Main system tuning question:

- can the Warehouse consume fast enough to keep the queue from growing without bound?

The team must explore:

1. optimal number of client threads,
2. optimal number of Warehouse consumer threads.

Ideal queue behavior:

- queue grows during ramp-up,
- then stabilizes near a flat plateau,
- ideally stays near zero,
- fewer than `1000` messages is an excellent target.

Bad queue behavior:

- queue length spikes sharply,
- then drains slowly,
- indicating producers are outrunning consumers.

The RabbitMQ management console should be used to observe:

- queue size over time,
- producer rate,
- consumer rate.

## 10. Warehouse Functional Requirements

When the Warehouse receives an order, it must update:

1. total quantity ordered for each product ID,
2. total number of orders.

At shutdown:

- Warehouse must print total number of orders.

For the report:

- a screenshot of the final Warehouse output is sufficient.

The PDF says you do not need to submit a CSV or long per-product listing.

## 11. Deliverables

### 11.1 Code / Config Deliverables

The repo must contain:

- Java code for Product, ShoppingCart, and CCA microservices,
- Docker files,
- Terraform files,
- RabbitMQ broker configuration,
- Warehouse consumer code,
- bad Product service code,
- ALB and target group configuration,
- any supporting configuration/scripts.

The code must be readable by the TAs and Professor.

### 11.2 Report Deliverables

The report PDF must contain:

1. all team member names,
2. repository URL,
3. screenshot showing ATW routing away from the bad Product instance,
4. screenshot of RabbitMQ queue size over time,
5. screenshot of Warehouse final output showing total orders.

## 12. Grading View

The assignment is graded as:

- `60 points` for code/config/deployment artifacts,
- `40 points` for the report.

From a practical perspective, the highest-risk grading items are:

- missing Publisher Confirms,
- missing Manual Acks,
- missing bad Product service,
- missing ALB path-based routing,
- missing ATW evidence,
- poor RabbitMQ queue behavior with no tuning evidence,
- missing AWS screenshots.

## 13. Recommended Architecture for Our Implementation

### 13.1 Container Layout

Recommended containers:

- `product-service-good-1`
- `product-service-good-2`
- `product-service-bad`
- `shopping-cart-service`
- `credit-card-authorizer`
- `rabbitmq`
- `warehouse-consumer`
- `load-test-client`

### 13.2 Suggested Runtime Topology

- ALB routes HTTP requests to:
  - Product target group
  - ShoppingCart target group
  - CreditCard target group
- ShoppingCart calls CCA over HTTP
- ShoppingCart publishes checkout messages to RabbitMQ
- Warehouse consumes messages from RabbitMQ

### 13.3 Suggested Storage Strategy

For this assignment, storage can stay simple unless the PDF or course repo imposes something stricter.

Suggested minimum persisted entities:

- products,
- shopping carts,
- cart items,
- orders.

If speed matters more than full realism, start with simple storage first and harden later.

## 14. Recommended Implementation Sequence

This is the practical order that minimizes risk.

### Phase 1. Confirm Interfaces and Project Skeleton

- lock down endpoint shapes from `improved_api.yaml`,
- decide service/module layout,
- scaffold all services,
- define shared message schema for RabbitMQ.

### Phase 2. Build Local Functional Version

- implement Product Service,
- implement ShoppingCart create/add-item/checkout,
- implement CCA validation and random authorization,
- implement RabbitMQ publish path,
- implement Warehouse consumer,
- implement order statistics tracking.

### Phase 3. Add Reliability Features

- add Publisher Confirms in SCS,
- add Manual Acks in Warehouse,
- ensure thread-safe Warehouse counters,
- test success and decline flows.

### Phase 4. Containerize Everything

- Dockerize each service separately,
- Dockerize RabbitMQ,
- Dockerize Warehouse consumer,
- verify all containers can communicate locally.

### Phase 5. AWS Deployment

- provision infrastructure with Terraform,
- deploy service containers,
- deploy RabbitMQ container,
- configure security groups/networking,
- deploy ALB and target groups,
- add listener rules for path-based routing.

### Phase 6. ALB / ATW Demonstration

- deploy 2 good Product instances,
- deploy 1 bad Product instance,
- configure health checks so bad instance stays in service,
- verify ATW shifts traffic away from bad instance,
- capture screenshots.

### Phase 7. Load Testing and RabbitMQ Tuning

- deploy load test client in AWS,
- run 200,000 checkout requests,
- vary client thread count,
- vary Warehouse consumer thread count,
- monitor queue length and throughput,
- capture screenshots,
- identify best configuration.

### Phase 8. Final Report and Submission

- gather screenshots,
- summarize routing and queue results,
- record team info and repo URL,
- verify all code is accessible,
- submit one final report per team.

## 15. Suggested Work Split for a Team

Because the PDF emphasizes teamwork, a clean split would help.

Suggested team ownership:

- Person 1: Product Service + bad Product variant
- Person 2: ShoppingCart Service
- Person 3: Credit Card Authorizer + API validation
- Person 4: RabbitMQ + Warehouse consumer
- Shared: Terraform, ALB, load testing, report

If the team has five members:

- split AWS/Terraform and load testing/report into separate ownership areas.

## 16. What We Can Do Right Now Without AWS Credentials

Even before learner credentials arrive, we can do a lot:

- finalize architecture,
- build all service skeletons,
- implement the local business flow,
- implement CCA rules,
- implement RabbitMQ producer/consumer logic,
- add Publisher Confirms,
- add Manual Acks,
- create Dockerfiles,
- prepare Terraform structure,
- prepare load test client code.

This is actually the best use of time because AWS credits should be saved until the local version is stable.

## 17. What Must Wait Until Learner Credentials Arrive

These tasks should wait for AWS access:

- provisioning cloud infrastructure,
- deploying containers to AWS hosts,
- creating ALB and target groups,
- validating ATW behavior in AWS,
- running the official AWS-based load test,
- capturing grading screenshots from AWS and RabbitMQ console.

## 18. Common Failure Points to Avoid

- returning checkout success before RabbitMQ confirms publish,
- using auto-ack instead of manual ack in Warehouse,
- making Warehouse single-threaded and letting queue explode,
- allowing the bad Product instance to fail health checks and get removed,
- load testing locally instead of from AWS,
- forgetting that Warehouse must be a separate container,
- forgetting to capture screenshots while the system is in the required state.

## 19. Immediate Next Steps for Us

Our best next moves are:

1. convert this outline into a concrete implementation plan for the repo,
2. choose the exact service structure and framework,
3. scaffold the code for all required services locally,
4. implement the checkout path end-to-end before touching AWS.

## 20. Working Summary

At a high level, Assignment 3 is asking for a cloud-deployed microservice system where:

- HTTP requests enter through an ALB,
- ShoppingCart orchestrates checkout,
- CreditCardAuthorizer simulates payment outcomes,
- RabbitMQ decouples checkout from warehouse processing,
- Warehouse consumes reliably and concurrently,
- AWS ATW demonstrates intelligent traffic shifting away from a degraded instance,
- and the whole system is tuned under heavy load.

If we execute the project in the right order, the assignment is very manageable. The main risk is not complexity, but missing one of the explicit "must" requirements hidden across the PDF.
