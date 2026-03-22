package com.commercehub.platform.cart;

public class ConcurrentCartModificationException extends RuntimeException {
    public ConcurrentCartModificationException(String shoppingCartId) {
        super("Shopping cart %s was modified concurrently; please retry the request".formatted(shoppingCartId));
    }
}
