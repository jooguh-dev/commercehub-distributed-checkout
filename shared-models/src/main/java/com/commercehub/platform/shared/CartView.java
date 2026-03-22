package com.commercehub.platform.shared;

import java.util.List;

public record CartView(String shoppingCartId, int customerId, String status, List<CartItemDto> items) {
}
