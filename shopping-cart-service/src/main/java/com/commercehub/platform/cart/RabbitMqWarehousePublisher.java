package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CheckoutMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Primary
public class RabbitMqWarehousePublisher implements WarehousePublisher, AutoCloseable {
    private final ObjectMapper objectMapper;
    private final ConnectionFactory connectionFactory;
    private final String queueName;
    private final String deadLetterExchange;
    private final String deadLetterQueue;
    private final String deadLetterRoutingKey;
    private final long publishTimeoutMillis;
    private final int maxAttempts;
    private final long retryBackoffMillis;
    private final Semaphore bulkhead;
    private final long bulkheadAcquireTimeoutMillis;
    private final int circuitBreakerFailureThreshold;
    private final long circuitBreakerOpenMillis;
    private final Clock clock;
    private final PublisherSleeper sleeper;
    private final ThreadLocal<ChannelHolder> channelHolder = ThreadLocal.withInitial(ChannelHolder::new);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long circuitOpenUntilEpochMillis;
    private volatile Connection connection;

    @Autowired
    public RabbitMqWarehousePublisher(ObjectMapper objectMapper,
                                      @Value("${rabbitmq.host}") String host,
                                      @Value("${rabbitmq.port}") int port,
                                      @Value("${rabbitmq.username}") String username,
                                      @Value("${rabbitmq.password}") String password,
                                      @Value("${rabbitmq.virtual-host}") String virtualHost,
                                      @Value("${rabbitmq.queue}") String queueName,
                                      @Value("${rabbitmq.dead-letter-exchange:}") String deadLetterExchange,
                                      @Value("${rabbitmq.dead-letter-queue:}") String deadLetterQueue,
                                      @Value("${rabbitmq.dead-letter-routing-key:}") String deadLetterRoutingKey,
                                      @Value("${rabbitmq.publish-timeout-ms}") long publishTimeoutMillis,
                                      @Value("${rabbitmq.retry.max-attempts:3}") int maxAttempts,
                                      @Value("${rabbitmq.retry.backoff-ms:100}") long retryBackoffMillis,
                                      @Value("${rabbitmq.bulkhead.max-concurrent-publishes:128}") int maxConcurrentPublishes,
                                      @Value("${rabbitmq.bulkhead.acquire-timeout-ms:250}") long bulkheadAcquireTimeoutMillis,
                                      @Value("${rabbitmq.circuit-breaker.failure-threshold:5}") int circuitBreakerFailureThreshold,
                                      @Value("${rabbitmq.circuit-breaker.open-ms:5000}") long circuitBreakerOpenMillis) {
        this(
                objectMapper,
                buildConnectionFactory(host, port, username, password, virtualHost),
                queueName,
                deadLetterExchange,
                deadLetterQueue,
                deadLetterRoutingKey,
                publishTimeoutMillis,
                maxAttempts,
                retryBackoffMillis,
                maxConcurrentPublishes,
                bulkheadAcquireTimeoutMillis,
                circuitBreakerFailureThreshold,
                circuitBreakerOpenMillis,
                Clock.systemUTC(),
                millis -> Thread.sleep(millis)
        );
    }

    RabbitMqWarehousePublisher(ObjectMapper objectMapper,
                               ConnectionFactory connectionFactory,
                               String queueName,
                               String deadLetterExchange,
                               String deadLetterQueue,
                               String deadLetterRoutingKey,
                               long publishTimeoutMillis,
                               int maxAttempts,
                               long retryBackoffMillis,
                               int maxConcurrentPublishes,
                               long bulkheadAcquireTimeoutMillis,
                               int circuitBreakerFailureThreshold,
                               long circuitBreakerOpenMillis,
                               Clock clock,
                               PublisherSleeper sleeper) {
        this.objectMapper = objectMapper;
        this.connectionFactory = connectionFactory;
        this.queueName = queueName;
        this.deadLetterExchange = normalizeOrDefault(deadLetterExchange, queueName + ".dlx");
        this.deadLetterQueue = normalizeOrDefault(deadLetterQueue, queueName + ".dlq");
        this.deadLetterRoutingKey = normalizeOrDefault(deadLetterRoutingKey, queueName);
        this.publishTimeoutMillis = publishTimeoutMillis;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMillis = Math.max(0L, retryBackoffMillis);
        this.bulkhead = new Semaphore(Math.max(1, maxConcurrentPublishes), true);
        this.bulkheadAcquireTimeoutMillis = Math.max(0L, bulkheadAcquireTimeoutMillis);
        this.circuitBreakerFailureThreshold = Math.max(1, circuitBreakerFailureThreshold);
        this.circuitBreakerOpenMillis = Math.max(0L, circuitBreakerOpenMillis);
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public boolean publish(CheckoutMessage message) {
        if (isCircuitOpen()) {
            return false;
        }

        boolean acquired = false;
        try {
            acquired = bulkhead.tryAcquire(bulkheadAcquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                registerFailure();
                return false;
            }

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (attempt > 1 && retryBackoffMillis > 0) {
                    sleeper.sleep(retryBackoffMillis);
                }

                try {
                    if (publishOnce(message)) {
                        registerSuccess();
                        return true;
                    }
                } catch (IOException | TimeoutException exception) {
                    invalidateCurrentChannel();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    registerFailure();
                    return false;
                }
            }

            registerFailure();
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            registerFailure();
            return false;
        } finally {
            if (acquired) {
                bulkhead.release();
            }
        }
    }

    @Override
    public void close() {
        invalidateCurrentChannel();
        Connection currentConnection = connection;
        connection = null;
        if (currentConnection != null) {
            try {
                currentConnection.close();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean publishOnce(CheckoutMessage message) throws IOException, TimeoutException, InterruptedException {
        Channel channel = currentChannel();
        channel.basicPublish("", queueName, null, objectMapper.writeValueAsBytes(message));
        boolean confirmed = channel.waitForConfirms(publishTimeoutMillis);
        if (!confirmed) {
            invalidateCurrentChannel();
        }
        return confirmed;
    }

    private Channel currentChannel() throws IOException, TimeoutException {
        ChannelHolder holder = channelHolder.get();
        if (holder.channel != null && holder.channel.isOpen()) {
            return holder.channel;
        }

        synchronized (holder) {
            if (holder.channel != null && holder.channel.isOpen()) {
                return holder.channel;
            }

            Channel channel = currentConnection().createChannel();
            RabbitMqQueueTopology.declare(channel, queueName, deadLetterExchange, deadLetterQueue, deadLetterRoutingKey);
            channel.confirmSelect();
            holder.channel = channel;
            return channel;
        }
    }

    private Connection currentConnection() throws IOException, TimeoutException {
        Connection current = connection;
        if (current != null && current.isOpen()) {
            return current;
        }

        synchronized (this) {
            current = connection;
            if (current != null && current.isOpen()) {
                return current;
            }

            if (current != null) {
                try {
                    current.close();
                } catch (Exception ignored) {
                }
            }

            connection = connectionFactory.newConnection();
            return connection;
        }
    }

    private void invalidateCurrentChannel() {
        ChannelHolder holder = channelHolder.get();
        Channel channel = holder.channel;
        holder.channel = null;
        if (channel != null) {
            try {
                channel.close();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isCircuitOpen() {
        return clock.millis() < circuitOpenUntilEpochMillis;
    }

    private void registerSuccess() {
        consecutiveFailures.set(0);
        circuitOpenUntilEpochMillis = 0L;
    }

    private void registerFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= circuitBreakerFailureThreshold) {
            circuitOpenUntilEpochMillis = clock.millis() + circuitBreakerOpenMillis;
        }
    }

    private static ConnectionFactory buildConnectionFactory(String host,
                                                            int port,
                                                            String username,
                                                            String password,
                                                            String virtualHost) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        return factory;
    }

    private static String normalizeOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static final class ChannelHolder {
        private Channel channel;
    }

    @FunctionalInterface
    interface PublisherSleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
