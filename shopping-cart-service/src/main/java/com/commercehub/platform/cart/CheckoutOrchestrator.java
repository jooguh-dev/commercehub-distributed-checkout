package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CartItemDto;
import com.commercehub.platform.shared.CheckoutMessage;

import java.util.List;

public class CheckoutOrchestrator {
    private final CreditCardAuthorizerClient creditCardAuthorizerClient;
    private final WarehousePublisher warehousePublisher;

    public CheckoutOrchestrator(CreditCardAuthorizerClient creditCardAuthorizerClient,
                                WarehousePublisher warehousePublisher) {
        this.creditCardAuthorizerClient = creditCardAuthorizerClient;
        this.warehousePublisher = warehousePublisher;
    }

    public boolean checkout(String orderId,
                            String shoppingCartId,
                            int customerId,
                            String creditCardNumber,
                            List<CartItemDto> items) {
        if (!creditCardAuthorizerClient.authorize(creditCardNumber)) {
            return false;
        }

        return warehousePublisher.publish(new CheckoutMessage(orderId, shoppingCartId, customerId, items));
    }
}
