# CS6650 Assignment 3

This repository is scaffolded as a Maven multi-module Java project for Assignment 3.

## Modules

- `shared-models`: shared DTOs and common request/response models
- `product-service`: Product microservice
- `shopping-cart-service`: Shopping cart orchestration service
- `credit-card-authorizer`: mock credit card authorization service
- `warehouse-consumer`: RabbitMQ warehouse consumer
- `load-test-client`: AWS load-testing client
- `infra`: Terraform and infrastructure files
- `docker`: container-related assets

## Namespace

- Maven `groupId`: `com.commercehub.platform`
- Maven root `artifactId`: `commerce-platform`
- Java package roots:
  - `com.commercehub.platform.product`
  - `com.commercehub.platform.cart`
  - `com.commercehub.platform.cca`
  - `com.commercehub.platform.warehouse`
  - `com.commercehub.platform.loadtest`

## Testing

- Unit tests use `JUnit 5`
- Mocked collaborator tests use `Mockito`

## Next Steps

- implement REST endpoints and persistence
- wire ShoppingCart to CreditCardAuthorizer and RabbitMQ
- implement Publisher Confirms and Manual Acknowledgements
- add Dockerfiles and Terraform

## RabbitMQ

- Shopping cart publishes checkout messages to `warehouse-orders`
- Publisher confirms are used before checkout success is returned
- Warehouse consumer uses manual acknowledgements after recording the order
- Invalid warehouse messages are dead-lettered to `warehouse-orders.dlq`
- `warehouse-consumer` includes a RabbitMQ integration test for DLQ routing when Docker/Testcontainers is available

## Local Run

Start the local stack:

```bash
docker compose up --build
```

Available endpoints:

- Shopping cart service: `http://localhost:18080`
- Product service: `http://localhost:18081`
- Bad product service: `http://localhost:28081`
- Credit card authorizer: `http://localhost:18082`
- RabbitMQ management console: `http://localhost:15673`

Example flow:

```bash
curl -X POST http://localhost:18080/shopping-cart \
  -H 'Content-Type: application/json' \
  -d '{"customerId":1}'
```

```bash
curl -X POST http://localhost:18080/shopping-carts/<shoppingCartId>/addItem \
  -H 'Content-Type: application/json' \
  -d '{"productId":1,"quantity":2}'
```

```bash
curl -X POST http://localhost:18080/shopping-carts/<shoppingCartId>/checkout \
  -H 'Content-Type: application/json' \
  -d '{"creditCardNumber":"1234-5678-9012-3456"}'
```

Small load test:

```bash
mvn -pl load-test-client -am package -DskipTests
java -jar load-test-client/target/load-test-client-0.0.1-SNAPSHOT.jar \
  --base-url=http://localhost:18080 \
  --requests=20 \
  --threads=4 \
  --product-id=1 \
  --quantity=1
```

Bad product instance demo:

```bash
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:28081/products/1
done
```

The bad instance uses the same image as `product-service` but enables `PRODUCT_BAD_INSTANCE_ENABLED=true`, so roughly half of `GET /products/{productId}` requests return `503`.

`product-service` starts with a shared seed catalog on every instance, so `productId` values `1`, `2`, and `3` are safe to use for local testing, ALB validation, and load tests.
