package com.commercehub.platform.cart;

public class ShoppingCartNotFoundException extends RuntimeException {
    public ShoppingCartNotFoundException(String shoppingCartId) {
        super("Shopping cart " + shoppingCartId + " was not found");
    }
}
