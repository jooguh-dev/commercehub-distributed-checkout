package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CheckoutMessage;

public interface WarehousePublisher {
    boolean publish(CheckoutMessage message);
}
