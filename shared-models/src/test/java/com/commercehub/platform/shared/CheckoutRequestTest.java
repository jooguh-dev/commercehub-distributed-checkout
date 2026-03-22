package com.commercehub.platform.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutRequestTest {

    @Test
    void storesCreditCardNumber() {
        CheckoutRequest request = new CheckoutRequest("1234-5678-9012-3456");

        assertEquals("1234-5678-9012-3456", request.creditCardNumber());
    }
}
