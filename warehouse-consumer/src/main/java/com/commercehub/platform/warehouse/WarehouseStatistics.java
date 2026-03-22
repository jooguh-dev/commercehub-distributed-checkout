package com.commercehub.platform.warehouse;

import com.commercehub.platform.shared.CheckoutMessage;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class WarehouseStatistics {
    private final LongAdder totalOrders = new LongAdder();
    private final Map<Integer, LongAdder> quantityByProductId = new ConcurrentHashMap<>();
    private final Set<String> processedOrderIds = ConcurrentHashMap.newKeySet();

    public void record(CheckoutMessage message) {
        if (!processedOrderIds.add(message.orderId())) {
            return;
        }
        totalOrders.increment();
        message.items().forEach(item ->
                quantityByProductId.computeIfAbsent(item.productId(), ignored -> new LongAdder()).add(item.quantity()));
    }

    public long totalOrders() {
        return totalOrders.sum();
    }

    public long quantityForProduct(int productId) {
        LongAdder adder = quantityByProductId.get(productId);
        return adder == null ? 0L : adder.sum();
    }

    public Map<Integer, Long> quantityByProductSnapshot() {
        Map<Integer, Long> snapshot = new TreeMap<>();
        quantityByProductId.forEach((productId, quantity) -> snapshot.put(productId, quantity.sum()));
        return snapshot;
    }

    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append("Total orders processed: ").append(totalOrders()).append(System.lineSeparator());
        quantityByProductSnapshot().forEach((productId, quantity) ->
                builder.append("Product ").append(productId).append(" total quantity: ").append(quantity).append(System.lineSeparator()));
        return builder.toString().trim();
    }
}
