package com.commercehub.platform.warehouse;

public record WarehouseConsumerConfig(
        String host,
        int port,
        String username,
        String password,
        String virtualHost,
        String queueName,
        String deadLetterExchange,
        String deadLetterQueue,
        String deadLetterRoutingKey,
        int consumerThreads,
        int prefetchCount,
        boolean requeueProcessingFailures
) {
    public static WarehouseConsumerConfig fromEnvironment() {
        String queueName = getString("RABBITMQ_QUEUE", "warehouse-orders");
        return new WarehouseConsumerConfig(
                getString("RABBITMQ_HOST", "localhost"),
                getInt("RABBITMQ_PORT", 5672),
                getString("RABBITMQ_USERNAME", "guest"),
                getString("RABBITMQ_PASSWORD", "guest"),
                getString("RABBITMQ_VHOST", "/"),
                queueName,
                getString("RABBITMQ_DEAD_LETTER_EXCHANGE", queueName + ".dlx"),
                getString("RABBITMQ_DEAD_LETTER_QUEUE", queueName + ".dlq"),
                getString("RABBITMQ_DEAD_LETTER_ROUTING_KEY", queueName),
                getInt("WAREHOUSE_CONSUMER_THREADS", 4),
                getInt("RABBITMQ_PREFETCH_COUNT", 100),
                getBoolean("WAREHOUSE_REQUEUE_PROCESSING_FAILURES", true)
        );
    }

    private static String getString(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int getInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
