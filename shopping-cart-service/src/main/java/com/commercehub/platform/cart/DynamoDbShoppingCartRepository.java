package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CartItemDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "cart.storage.mode", havingValue = "dynamodb")
public class DynamoDbShoppingCartRepository implements ShoppingCartRepository {
    private static final String PARTITION_KEY = "pk";
    private static final String ENTITY_TYPE = "entity_type";
    private static final String CART_ID = "shopping_cart_id";
    private static final String CUSTOMER_ID = "customer_id";
    private static final String STATUS = "status";
    private static final String ITEMS_JSON = "items_json";
    private static final String VERSION = "version";
    private static final String CHECKOUT_STARTED_AT = "checkout_started_at";
    private static final String CART_ENTITY = "CART";
    private static final TypeReference<List<CartItemDto>> CART_ITEM_LIST_TYPE = new TypeReference<>() { };

    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final boolean consistentRead;

    public DynamoDbShoppingCartRepository(DynamoDbClient dynamoDbClient,
                                          ObjectMapper objectMapper,
                                          @Value("${cart.storage.dynamodb.table}") String tableName,
                                          @Value("${cart.storage.dynamodb.consistent-read:true}") boolean consistentRead) {
        this.dynamoDbClient = dynamoDbClient;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
        this.consistentRead = consistentRead;
    }

    @Override
    public ShoppingCart create(int customerId) {
        ShoppingCart cart = new ShoppingCart(UUID.randomUUID().toString(), customerId);
        putNewCart(cart);
        return cart;
    }

    @Override
    public Optional<ShoppingCart> findById(String shoppingCartId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(PARTITION_KEY, AttributeValue.fromS(cartKey(shoppingCartId))))
                .consistentRead(consistentRead)
                .build()).item();

        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(toShoppingCart(item));
    }

    @Override
    public ShoppingCart save(ShoppingCart shoppingCart) {
        long nextVersion = shoppingCart.version() + 1;
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(PARTITION_KEY, AttributeValue.fromS(cartKey(shoppingCart.shoppingCartId()))))
                    .updateExpression(
                            "SET #entityType = :entityType, " +
                            "#cartId = :cartId, " +
                            "#customerId = :customerId, " +
                            "#status = :status, " +
                            "#version = :nextVersion, " +
                            "#checkoutStartedAt = :checkoutStartedAt, " +
                            "#itemsJson = :itemsJson"
                    )
                    .conditionExpression("attribute_exists(#pk) AND #version = :expectedVersion")
                    .expressionAttributeNames(Map.of(
                            "#pk", PARTITION_KEY,
                            "#entityType", ENTITY_TYPE,
                            "#cartId", CART_ID,
                            "#customerId", CUSTOMER_ID,
                            "#status", STATUS,
                            "#version", VERSION,
                            "#checkoutStartedAt", CHECKOUT_STARTED_AT,
                            "#itemsJson", ITEMS_JSON
                    ))
                    .expressionAttributeValues(Map.of(
                            ":entityType", AttributeValue.fromS(CART_ENTITY),
                            ":cartId", AttributeValue.fromS(shoppingCart.shoppingCartId()),
                            ":customerId", AttributeValue.fromN(Integer.toString(shoppingCart.customerId())),
                            ":status", AttributeValue.fromS(shoppingCart.status()),
                            ":nextVersion", AttributeValue.fromN(Long.toString(nextVersion)),
                            ":expectedVersion", AttributeValue.fromN(Long.toString(shoppingCart.version())),
                            ":checkoutStartedAt", nullableNumber(shoppingCart.checkoutStartedAtEpochMillis()),
                            ":itemsJson", AttributeValue.fromS(writeItems(shoppingCart.itemsView()))
                    ))
                    .build());
        } catch (software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException exception) {
            throw new ConcurrentCartModificationException(shoppingCart.shoppingCartId());
        }
        shoppingCart.markPersisted(nextVersion);
        return shoppingCart.copy();
    }

    @Override
    public String nextOrderId() {
        return UUID.randomUUID().toString();
    }

    private ShoppingCart toShoppingCart(Map<String, AttributeValue> item) {
        try {
            List<CartItemDto> items = objectMapper.readValue(item.get(ITEMS_JSON).s(), CART_ITEM_LIST_TYPE);
            return ShoppingCart.restore(
                    item.get(CART_ID).s(),
                    Integer.parseInt(item.get(CUSTOMER_ID).n()),
                    parseVersion(item),
                    parseCheckoutStartedAt(item),
                    item.get(STATUS).s(),
                    items
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize shopping cart items from DynamoDB", exception);
        }
    }

    private String writeItems(List<CartItemDto> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize shopping cart items for DynamoDB", exception);
        }
    }

    private String cartKey(String shoppingCartId) {
        return "CART#" + shoppingCartId;
    }

    private long parseVersion(Map<String, AttributeValue> item) {
        AttributeValue versionAttribute = item.get(VERSION);
        return versionAttribute == null ? 0 : Long.parseLong(versionAttribute.n());
    }

    private Long parseCheckoutStartedAt(Map<String, AttributeValue> item) {
        AttributeValue startedAtAttribute = item.get(CHECKOUT_STARTED_AT);
        if (startedAtAttribute == null || Boolean.TRUE.equals(startedAtAttribute.nul())) {
            return null;
        }
        return Long.parseLong(startedAtAttribute.n());
    }

    private AttributeValue nullableNumber(Long value) {
        return value == null ? AttributeValue.builder().nul(true).build() : AttributeValue.fromN(Long.toString(value));
    }

    private void putNewCart(ShoppingCart shoppingCart) {
        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            PARTITION_KEY, AttributeValue.fromS(cartKey(shoppingCart.shoppingCartId())),
                            ENTITY_TYPE, AttributeValue.fromS(CART_ENTITY),
                            CART_ID, AttributeValue.fromS(shoppingCart.shoppingCartId()),
                            CUSTOMER_ID, AttributeValue.fromN(Integer.toString(shoppingCart.customerId())),
                            STATUS, AttributeValue.fromS(shoppingCart.status()),
                            VERSION, AttributeValue.fromN(Long.toString(shoppingCart.version())),
                            CHECKOUT_STARTED_AT, nullableNumber(shoppingCart.checkoutStartedAtEpochMillis()),
                            ITEMS_JSON, AttributeValue.fromS(writeItems(shoppingCart.itemsView()))
                    ))
                    .conditionExpression("attribute_not_exists(#pk)")
                    .expressionAttributeNames(Map.of("#pk", PARTITION_KEY))
                    .build());
        } catch (software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException exception) {
            throw new ConcurrentCartModificationException(shoppingCart.shoppingCartId());
        }
    }
}
