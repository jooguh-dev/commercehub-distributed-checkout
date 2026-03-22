package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CheckoutMessage;
import org.springframework.stereotype.Component;

@Component
public class InMemoryWarehousePublisher implements WarehousePublisher {

    @Override
    public boolean publish(CheckoutMessage message) {
        return true;
    }
}
