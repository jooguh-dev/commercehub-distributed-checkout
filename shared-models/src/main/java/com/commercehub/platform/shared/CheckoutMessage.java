package com.commercehub.platform.shared;

import java.util.List;

public record CheckoutMessage(
        String orderId,
        String shoppingCartId,
        int customerId,
        List<CartItemDto> items
) {
}
