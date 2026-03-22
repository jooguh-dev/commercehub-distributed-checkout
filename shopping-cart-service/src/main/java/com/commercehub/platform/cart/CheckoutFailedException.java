package com.commercehub.platform.cart;

public class CheckoutFailedException extends RuntimeException {
    public CheckoutFailedException(String message) {
        super(message);
    }
}
