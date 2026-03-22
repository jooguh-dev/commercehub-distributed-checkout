package com.commercehub.platform.warehouse;

import com.commercehub.platform.shared.CheckoutMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class WarehouseMessageProcessor {
    private final ObjectMapper objectMapper;
    private final WarehouseStatistics warehouseStatistics;

    public WarehouseMessageProcessor(ObjectMapper objectMapper, WarehouseStatistics warehouseStatistics) {
        this.objectMapper = objectMapper;
        this.warehouseStatistics = warehouseStatistics;
    }

    public void process(byte[] body) throws IOException {
        try {
            CheckoutMessage message = objectMapper.readValue(body, CheckoutMessage.class);
            warehouseStatistics.record(message);
        } catch (JsonProcessingException exception) {
            throw new InvalidWarehouseMessageException("Failed to deserialize warehouse checkout message", exception);
        }
    }
}
