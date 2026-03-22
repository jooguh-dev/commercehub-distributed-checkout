package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CartItemDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShoppingCart {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CHECKOUT_IN_PROGRESS = "CHECKOUT_IN_PROGRESS";
    public static final String STATUS_CHECKED_OUT = "CHECKED_OUT";

    private final String shoppingCartId;
    private final int customerId;
    private final List<CartItemDto> items = new ArrayList<>();
    private long version;
    private Long checkoutStartedAtEpochMillis;
    private String status = STATUS_ACTIVE;

    public ShoppingCart(String shoppingCartId, int customerId) {
        this(shoppingCartId, customerId, 0, null, STATUS_ACTIVE, List.of());
    }

    private ShoppingCart(String shoppingCartId,
                         int customerId,
                         long version,
                         Long checkoutStartedAtEpochMillis,
                         String status,
                         List<CartItemDto> items) {
        this.shoppingCartId = shoppingCartId;
        this.customerId = customerId;
        this.version = version;
        this.checkoutStartedAtEpochMillis = checkoutStartedAtEpochMillis;
        this.status = status;
        this.items.addAll(items);
    }

    public static ShoppingCart restore(String shoppingCartId,
                                       int customerId,
                                       long version,
                                       Long checkoutStartedAtEpochMillis,
                                       String status,
                                       List<CartItemDto> items) {
        return new ShoppingCart(shoppingCartId, customerId, version, checkoutStartedAtEpochMillis, status, items);
    }

    public String shoppingCartId() {
        return shoppingCartId;
    }

    public int customerId() {
        return customerId;
    }

    public long version() {
        return version;
    }

    public String status() {
        return status;
    }

    public Long checkoutStartedAtEpochMillis() {
        return checkoutStartedAtEpochMillis;
    }

    public List<CartItemDto> items() {
        return List.copyOf(items);
    }

    List<CartItemDto> itemsView() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(CartItemDto item) {
        items.add(item);
    }

    public void markCheckedOut() {
        this.status = STATUS_CHECKED_OUT;
    }

    public void markCheckoutInProgress(long startedAtEpochMillis) {
        this.status = STATUS_CHECKOUT_IN_PROGRESS;
        this.checkoutStartedAtEpochMillis = startedAtEpochMillis;
    }

    public void markActive() {
        this.status = STATUS_ACTIVE;
        this.checkoutStartedAtEpochMillis = null;
    }

    public void markPersisted(long persistedVersion) {
        this.version = persistedVersion;
    }

    public ShoppingCart copy() {
        return new ShoppingCart(shoppingCartId, customerId, version, checkoutStartedAtEpochMillis, status, items);
    }
}
