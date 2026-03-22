package com.commercehub.platform.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class WarehouseConsumerApplication {

    private WarehouseConsumerApplication() {
    }

    public static void main(String[] args) throws Exception {
        WarehouseConsumerConfig config = WarehouseConsumerConfig.fromEnvironment();
        WarehouseStatistics warehouseStatistics = new WarehouseStatistics();
        WarehouseConsumerRunner runner = new WarehouseConsumerRunner(config, warehouseStatistics, new ObjectMapper());
        runner.runUntilShutdown();
    }
}
