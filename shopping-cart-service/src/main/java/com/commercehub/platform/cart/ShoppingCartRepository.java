package com.commercehub.platform.cart;

import java.util.Optional;

public interface ShoppingCartRepository {
    ShoppingCart create(int customerId);

    Optional<ShoppingCart> findById(String shoppingCartId);

    ShoppingCart save(ShoppingCart shoppingCart);

    String nextOrderId();
}
