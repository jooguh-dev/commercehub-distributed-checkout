package com.commercehub.platform.warehouse;

import com.commercehub.platform.shared.CartItemDto;
import com.commercehub.platform.shared.CheckoutMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseStatisticsTest {

    @Test
    void tracksOrderCountAndProductTotals() {
        WarehouseStatistics statistics = new WarehouseStatistics();

        statistics.record(new CheckoutMessage("order-1", "cart-2", 3, List.of(new CartItemDto(11, 2), new CartItemDto(12, 5))));
        statistics.record(new CheckoutMessage("order-2", "cart-3", 4, List.of(new CartItemDto(11, 4))));

        assertEquals(2L, statistics.totalOrders());
        assertEquals(6L, statistics.quantityForProduct(11));
        assertEquals(5L, statistics.quantityForProduct(12));
        assertEquals(Map.of(11, 6L, 12, 5L), statistics.quantityByProductSnapshot());
        assertTrue(statistics.summary().contains("Total orders processed: 2"));
        assertTrue(statistics.summary().contains("Product 11 total quantity: 6"));
    }

    @Test
    void ignoresDuplicateOrderIds() {
        WarehouseStatistics statistics = new WarehouseStatistics();

        statistics.record(new CheckoutMessage("order-1", "cart-2", 3, List.of(new CartItemDto(11, 2), new CartItemDto(12, 5))));
        statistics.record(new CheckoutMessage("order-1", "cart-2", 3, List.of(new CartItemDto(11, 2), new CartItemDto(12, 5))));

        assertEquals(1L, statistics.totalOrders());
        assertEquals(2L, statistics.quantityForProduct(11));
        assertEquals(5L, statistics.quantityForProduct(12));
    }
}
