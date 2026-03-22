FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY shared-models shared-models
COPY product-service product-service
COPY shopping-cart-service shopping-cart-service
COPY credit-card-authorizer credit-card-authorizer
COPY warehouse-consumer warehouse-consumer
COPY load-test-client load-test-client

RUN mvn -pl shopping-cart-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/shopping-cart-service/target/shopping-cart-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
