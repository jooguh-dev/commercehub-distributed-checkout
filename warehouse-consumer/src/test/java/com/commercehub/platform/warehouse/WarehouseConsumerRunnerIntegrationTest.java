package com.commercehub.platform.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseConsumerRunnerIntegrationTest {
    @Test
    void routesInvalidMessagesToDeadLetterQueue() throws Exception {
        Assumptions.assumeTrue(isDockerAvailable(), "Docker is required for the RabbitMQ integration test.");

        String queueName = "warehouse-orders-it-" + UUID.randomUUID();
        try (RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))) {
            rabbitmq.start();

            WarehouseConsumerConfig config = new WarehouseConsumerConfig(
                    rabbitmq.getHost(),
                    rabbitmq.getAmqpPort(),
                    "guest",
                    "guest",
                    "/",
                    queueName,
                    queueName + ".dlx",
                    queueName + ".dlq",
                    queueName,
                    1,
                    1,
                    true
            );

            WarehouseStatistics statistics = new WarehouseStatistics();
            WarehouseConsumerRunner runner = new WarehouseConsumerRunner(config, statistics, new ObjectMapper());
            CountDownLatch stopSignal = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(() -> {
                runner.runUntilSignal(stopSignal);
                return null;
            });

            try (Connection connection = newConnectionFactory(rabbitmq).newConnection();
                 Channel channel = connection.createChannel()) {
                awaitQueueReady(channel, queueName + ".dlq");
                channel.basicPublish("", queueName, null, "not-json".getBytes(StandardCharsets.UTF_8));

                long dlqCount = awaitMessageCount(channel, queueName + ".dlq", 1L);
                long mainQueueCount = channel.queueDeclarePassive(queueName).getMessageCount();

                assertEquals(1L, dlqCount);
                assertEquals(0L, mainQueueCount);
                assertEquals(0L, statistics.totalOrders());
            } finally {
                stopSignal.countDown();
                awaitRunnerShutdown(future);
                executor.shutdownNow();
            }
        }
    }

    private ConnectionFactory newConnectionFactory(RabbitMQContainer rabbitmq) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(rabbitmq.getHost());
        connectionFactory.setPort(rabbitmq.getAmqpPort());
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
        connectionFactory.setVirtualHost("/");
        return connectionFactory;
    }

    private boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void awaitQueueReady(Channel channel, String queueName) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try {
                channel.queueDeclarePassive(queueName);
                return;
            } catch (Exception exception) {
                Thread.sleep(100);
            }
        }
        throw new TimeoutException("Timed out waiting for queue " + queueName);
    }

    private long awaitMessageCount(Channel channel, String queueName, long expectedMinimum) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        long observed = 0L;
        while (System.nanoTime() < deadline) {
            observed = channel.queueDeclarePassive(queueName).getMessageCount();
            if (observed >= expectedMinimum) {
                return observed;
            }
            Thread.sleep(100);
        }
        throw new TimeoutException("Timed out waiting for " + queueName + " to reach " + expectedMinimum + " messages; observed " + observed);
    }

    private void awaitRunnerShutdown(Future<?> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw new RuntimeException(cause);
        }
        assertTrue(future.isDone());
    }
}
