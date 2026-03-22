FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY shared-models shared-models
COPY product-service product-service
COPY shopping-cart-service shopping-cart-service
COPY credit-card-authorizer credit-card-authorizer
COPY warehouse-consumer warehouse-consumer
COPY load-test-client load-test-client

RUN mvn -pl load-test-client -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/load-test-client/target/load-test-client-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
