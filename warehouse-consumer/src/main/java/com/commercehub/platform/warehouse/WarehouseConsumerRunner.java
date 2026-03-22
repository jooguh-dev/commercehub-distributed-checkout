package com.commercehub.platform.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

public class WarehouseConsumerRunner {
    private final WarehouseConsumerConfig config;
    private final WarehouseStatistics warehouseStatistics;
    private final WarehouseMessageProcessor warehouseMessageProcessor;
    private final List<Channel> channels = new ArrayList<>();
    private Connection connection;

    public WarehouseConsumerRunner(WarehouseConsumerConfig config,
                                   WarehouseStatistics warehouseStatistics,
                                   ObjectMapper objectMapper) {
        this.config = config;
        this.warehouseStatistics = warehouseStatistics;
        this.warehouseMessageProcessor = new WarehouseMessageProcessor(objectMapper, warehouseStatistics);
    }

    public void runUntilShutdown() throws IOException, TimeoutException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            latch.countDown();
        }));
        runUntilSignal(latch);
    }

    void runUntilSignal(CountDownLatch latch) throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(config.host());
        connectionFactory.setPort(config.port());
        connectionFactory.setUsername(config.username());
        connectionFactory.setPassword(config.password());
        connectionFactory.setVirtualHost(config.virtualHost());

        connection = connectionFactory.newConnection();

        try {
            for (int i = 0; i < config.consumerThreads(); i++) {
                Channel channel = connection.createChannel();
                channels.add(channel);
                WarehouseQueueTopology.declare(
                        channel,
                        config.queueName(),
                        config.deadLetterExchange(),
                        config.deadLetterQueue(),
                        config.deadLetterRoutingKey()
                );
                channel.basicQos(config.prefetchCount());

                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    try {
                        warehouseMessageProcessor.process(delivery.getBody());
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (InvalidWarehouseMessageException exception) {
                        channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
                    } catch (Exception exception) {
                        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, config.requeueProcessingFailures());
                    }
                };

                CancelCallback cancelCallback = consumerTag -> { };
                channel.basicConsume(config.queueName(), false, deliverCallback, cancelCallback);
            }

            latch.await();
        } finally {
            printSummary();
            close();
        }
    }

    public void printSummary() {
        System.out.println(warehouseStatistics.summary());
    }

    public void close() {
        for (Channel channel : channels) {
            try {
                channel.close();
            } catch (Exception ignored) {
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }
}
