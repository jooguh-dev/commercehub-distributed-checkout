package com.commercehub.platform.cart;

public class InvalidCartStateException extends RuntimeException {
    public InvalidCartStateException(String message) {
        super(message);
    }
}
