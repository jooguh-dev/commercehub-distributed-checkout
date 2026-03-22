package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CartItemDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamoDbShoppingCartRepositoryTest {

    @Test
    void saveUsesConditionalUpdateInsteadOfWholeItemPut() {
        AtomicReference<UpdateItemRequest> capturedRequest = new AtomicReference<>();
        DynamoDbClient dynamoDbClient = (DynamoDbClient) Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class[]{DynamoDbClient.class},
                (proxy, method, args) -> {
                    if ("updateItem".equals(method.getName())) {
                        capturedRequest.set((UpdateItemRequest) args[0]);
                        return null;
                    }
                    if ("serviceName".equals(method.getName())) {
                        return "DynamoDb";
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException("Unexpected method: " + method.getName());
                }
        );

        DynamoDbShoppingCartRepository repository = new DynamoDbShoppingCartRepository(
                dynamoDbClient,
                new ObjectMapper(),
                "cart-table",
                true
        );

        ShoppingCart cart = ShoppingCart.restore(
                "cart-123",
                7,
                3,
                12345L,
                ShoppingCart.STATUS_CHECKOUT_IN_PROGRESS,
                java.util.List.of(new CartItemDto(11, 2))
        );

        repository.save(cart);

        UpdateItemRequest request = capturedRequest.get();
        assertEquals("cart-table", request.tableName());
        assertEquals("CART#cart-123", request.key().get("pk").s());
        assertEquals("attribute_exists(#pk) AND #version = :expectedVersion", request.conditionExpression());
        assertEquals("4", request.expressionAttributeValues().get(":nextVersion").n());
        assertEquals("3", request.expressionAttributeValues().get(":expectedVersion").n());
        assertEquals("cart-123", request.expressionAttributeValues().get(":cartId").s());
        assertEquals("7", request.expressionAttributeValues().get(":customerId").n());
        assertEquals(ShoppingCart.STATUS_CHECKOUT_IN_PROGRESS, request.expressionAttributeValues().get(":status").s());
        assertEquals("12345", request.expressionAttributeValues().get(":checkoutStartedAt").n());
        assertEquals("[{\"productId\":11,\"quantity\":2}]", request.expressionAttributeValues().get(":itemsJson").s());
        assertEquals(
                Map.of(
                        "#pk", "pk",
                        "#entityType", "entity_type",
                        "#cartId", "shopping_cart_id",
                        "#customerId", "customer_id",
                        "#status", "status",
                        "#version", "version",
                        "#checkoutStartedAt", "checkout_started_at",
                        "#itemsJson", "items_json"
                ),
                request.expressionAttributeNames()
        );
    }
}
