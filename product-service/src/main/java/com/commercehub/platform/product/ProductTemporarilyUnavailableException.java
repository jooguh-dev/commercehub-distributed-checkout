package com.commercehub.platform.product;

public class ProductTemporarilyUnavailableException extends RuntimeException {
    public ProductTemporarilyUnavailableException() {
        super("Product service temporarily unavailable");
    }
}
