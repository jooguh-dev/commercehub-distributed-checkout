package com.commercehub.platform.cart;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class ShoppingCartStorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "cart.storage.mode", havingValue = "dynamodb")
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.create();
    }
}
