package com.commercehub.platform.product;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(int productId) {
        super("Product " + productId + " was not found");
    }
}
