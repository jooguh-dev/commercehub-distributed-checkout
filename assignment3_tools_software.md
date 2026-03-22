# CS6650 Assignment 3 Tools and Software Plan

## 1. Recommended Core Stack

### 1.1 Language

- Java

Reason:

- The assignment explicitly refers to Java code in several places.
- The RabbitMQ sample code is in Java.
- The Warehouse client is explicitly expected as a Java RabbitMQ client.

### 1.2 Build Tool

- Maven

Reason:

- straightforward for multi-module Java projects,
- simple dependency management,
- familiar with JUnit/Mockito/Spring Boot style setups,
- easy to package for Docker builds.

### 1.3 Testing Stack

- `JUnit 5`
- `Mockito`

Testing rule:

- use `Mockito` for unit tests when mocking:
  - HTTP clients,
  - repositories,
  - RabbitMQ publisher wrappers,
  - random outcome providers,
  - service dependencies.

### 1.4 Containerization

- Docker
- Docker Compose for local multi-service testing

### 1.5 Infrastructure as Code

- Terraform

Reason:

- explicitly required by the assignment deliverables,
- good fit for ALB, target groups, and AWS infrastructure setup.

## 2. Recommended Service Frameworks

### 2.1 HTTP Microservices

Recommended:

- Spring Boot

Reason:

- fast to scaffold REST APIs,
- strong support for validation,
- easy testing with JUnit and Mockito,
- mature ecosystem for HTTP clients and configuration,
- easy health endpoints for ALB checks.

Alternative:

- plain Java frameworks are possible, but Spring Boot will likely reduce risk and speed up development.

### 2.2 HTTP Client Between Services

Recommended:

- Spring `RestClient` or `WebClient`

Reason:

- clean integration for calling Credit Card Authorizer from ShoppingCart,
- easy to mock in unit tests by wrapping the client in your own adapter/service layer.

### 2.3 Validation

Recommended:

- Jakarta Bean Validation

Use for:

- request DTO validation,
- credit card format validation,
- quantity bounds,
- ID sanity checks.

## 3. Messaging Tools

### 3.1 Message Broker

- RabbitMQ

Required features to use:

- publisher confirms,
- manual consumer acknowledgements,
- management console.

### 3.2 Java RabbitMQ Client

- official RabbitMQ Java client library

Use for:

- ShoppingCart publisher,
- Warehouse consumer.

### 3.3 Thread Safety Utilities

Recommended Java utilities:

- `ConcurrentHashMap`
- `AtomicInteger`
- `LongAdder`
- executor/thread pool utilities from `java.util.concurrent`

Use for:

- Warehouse counters,
- multi-threaded consumer bookkeeping.

## 4. AWS Services We Will Need

These are the main cloud services implied by the assignment.

### 4.1 Compute

- EC2

Likely use:

- run service containers,
- run RabbitMQ container,
- run Warehouse consumer container,
- run load test client.

### 4.2 Load Balancing

- AWS Application Load Balancer

Required use:

- path-based routing,
- multiple target groups,
- ATW demonstration.

### 4.3 Networking / Security

- VPC components provided by learner environment or Terraform-managed
- Security Groups

Needed for:

- ALB to service traffic,
- inter-service calls,
- RabbitMQ port access,
- management console access if allowed.

### 4.4 Monitoring / Evidence

- AWS Console screenshots
- RabbitMQ Management Console screenshots

Needed for report evidence.

## 5. Local Development Software

Each team member should ideally have:

- Java JDK
- Maven
- Docker Desktop or Docker Engine
- Docker Compose support
- Git
- an IDE such as IntelliJ IDEA

Useful CLI tools:

- `curl`
- `jq`
- `rg`

## 6. Recommended Project Dependencies by Service

### 6.1 Product Service

- Spring Boot web starter
- validation starter
- test starter
- Mockito

### 6.2 ShoppingCart Service

- Spring Boot web starter
- validation starter
- RabbitMQ Java client
- HTTP client dependency
- test starter
- Mockito

### 6.3 Credit Card Authorizer

- Spring Boot web starter
- validation starter
- test starter
- Mockito

### 6.4 Warehouse Consumer

- RabbitMQ Java client
- logging library
- JUnit 5
- Mockito

### 6.5 Load Test Client

- Java HTTP client or Apache HttpClient
- concurrency utilities
- metrics/timing utilities

## 7. Testing Tooling Plan

### 7.1 Unit Tests

Use:

- `JUnit 5`
- `Mockito`

Purpose:

- verify service logic without needing live RabbitMQ or AWS,
- validate checkout orchestration behavior,
- validate CCA behavior,
- validate Warehouse processing logic.

### 7.2 Integration Tests

Use:

- local Docker Compose environment,
- real RabbitMQ container,
- real service containers or local service processes.

Purpose:

- verify service-to-service HTTP behavior,
- verify RabbitMQ publish/consume path,
- verify checkout end-to-end.

### 7.3 Load Tests

Use:

- custom Java load test client deployed in AWS.

Purpose:

- send `200,000` checkout requests,
- measure throughput,
- tune client threads and Warehouse consumer threads,
- observe queue size over time.

## 8. Suggested Mocking Strategy with Mockito

To keep tests clean, do not mock framework internals directly where avoidable.

Recommended approach:

- create a small `CreditCardAuthorizerClient` abstraction,
- create a small `WarehousePublisher` abstraction,
- mock those abstractions in ShoppingCart unit tests using `Mockito`,
- isolate randomness in CCA behind a helper/service so outcomes can be controlled in tests.

Example areas to mock with Mockito:

- CCA client response
- RabbitMQ publish success/failure
- repository behavior
- clock/random providers when needed

## 9. Nice-to-Have Supporting Tools

Useful but not mandatory:

- Postman or Bruno for API exploration
- Mermaid for architecture diagrams in docs
- Checkstyle or Spotless for code consistency
- GitHub Actions for CI if the team wants quick feedback

## 10. Minimal Software Checklist

Before coding starts, confirm the team has:

- [ ] Java JDK installed
- [ ] Maven installed
- [ ] Docker installed
- [ ] Docker Compose available
- [ ] RabbitMQ runnable via container
- [ ] IDE ready
- [ ] Git repo initialized/shared
- [ ] JUnit 5 configured
- [ ] Mockito configured
- [ ] Terraform installed
- [ ] AWS CLI ready for later deployment

## 11. Recommended Final Tool Choices

If we optimize for speed and low risk, the cleanest choice set is:

- Java
- Maven
- Spring Boot
- JUnit 5
- Mockito
- RabbitMQ
- Docker
- Docker Compose
- Terraform
- AWS ALB on EC2-hosted containers
