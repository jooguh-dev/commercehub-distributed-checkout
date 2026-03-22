package com.commercehub.platform.cart;

public interface CreditCardAuthorizerClient {
    boolean authorize(String creditCardNumber);
}
