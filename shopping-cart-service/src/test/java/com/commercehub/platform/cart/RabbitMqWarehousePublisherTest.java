package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CartItemDto;
import com.commercehub.platform.shared.CheckoutMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMqWarehousePublisherTest {
    private final CheckoutMessage message = new CheckoutMessage("order-1", "cart-2", 3, List.of(new CartItemDto(10, 2)));

    @Test
    void returnsFalseWhenBrokerIsUnavailable() {
        RabbitMqWarehousePublisher publisher = new RabbitMqWarehousePublisher(
                new ObjectMapper(),
                "127.0.0.1",
                1,
                "guest",
                "guest",
                "/",
                "warehouse-orders",
                "",
                "",
                "",
                100,
                1,
                0,
                1,
                0,
                2,
                1000
        );

        boolean result = publisher.publish(message);

        assertFalse(result);
    }

    @Test
    void reusesConnectionAndChannelAcrossSuccessfulPublishes() {
        StubBroker broker = new StubBroker(List.of(true, true));

        RabbitMqWarehousePublisher publisher = broker.publisher(1, 3, 1000);

        assertTrue(publisher.publish(message));
        assertTrue(publisher.publish(message));
        assertTrue(broker.connectionFactory.creates.get() == 1);
        assertTrue(broker.channelCreates.get() == 1);
        assertTrue(broker.queueDeclareCalls.get() == 2);
        assertTrue(broker.confirmSelectCalls.get() == 1);
        assertTrue(broker.publishCalls.get() == 2);
    }

    @Test
    void retriesBeforeFailingAndOpensCircuitBreaker() {
        StubBroker broker = new StubBroker(List.of(false, false, true));
        MutableClock clock = new MutableClock(Instant.parse("2026-03-22T00:00:00Z"), ZoneOffset.UTC);

        RabbitMqWarehousePublisher publisher = broker.publisher(clock, 2, 1, 5000);

        assertFalse(publisher.publish(message));
        assertFalse(publisher.publish(message));
        assertTrue(broker.publishCalls.get() == 2);

        clock.advanceSeconds(6);
        assertTrue(publisher.publish(message));
        assertTrue(broker.publishCalls.get() == 3);
    }

    private static final class StubBroker {
        private final AtomicInteger channelCreates = new AtomicInteger();
        private final AtomicInteger queueDeclareCalls = new AtomicInteger();
        private final AtomicInteger confirmSelectCalls = new AtomicInteger();
        private final AtomicInteger publishCalls = new AtomicInteger();
        private final List<Boolean> confirmResults;
        private final AtomicInteger confirmIndex = new AtomicInteger();
        private final StubConnectionFactory connectionFactory;

        private StubBroker(List<Boolean> confirmResults) {
            this.confirmResults = confirmResults;
            this.connectionFactory = new StubConnectionFactory(connectionProxy());
        }

        private RabbitMqWarehousePublisher publisher(int maxAttempts,
                                                     int circuitBreakerThreshold,
                                                     long circuitBreakerOpenMillis) {
            return publisher(new MutableClock(Instant.parse("2026-03-22T00:00:00Z"), ZoneOffset.UTC),
                    maxAttempts,
                    circuitBreakerThreshold,
                    circuitBreakerOpenMillis);
        }

        private RabbitMqWarehousePublisher publisher(MutableClock clock,
                                                     int maxAttempts,
                                                     int circuitBreakerThreshold,
                                                     long circuitBreakerOpenMillis) {
            return new RabbitMqWarehousePublisher(
                    new ObjectMapper(),
                    connectionFactory,
                    "warehouse-orders",
                    "",
                    "",
                    "",
                    100,
                    maxAttempts,
                    0,
                    4,
                    0,
                    circuitBreakerThreshold,
                    circuitBreakerOpenMillis,
                    clock,
                    millis -> { }
            );
        }

        private Connection connectionProxy() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isOpen" -> true;
                        case "createChannel" -> {
                            channelCreates.incrementAndGet();
                            yield channelProxy();
                        }
                        case "close", "abort" -> null;
                        case "toString" -> "StubConnection";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private Channel channelProxy() {
            return (Channel) Proxy.newProxyInstance(
                    Channel.class.getClassLoader(),
                    new Class[]{Channel.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isOpen" -> true;
                        case "queueDeclare" -> {
                            queueDeclareCalls.incrementAndGet();
                            yield null;
                        }
                        case "exchangeDeclare", "queueBind" -> null;
                        case "confirmSelect" -> {
                            confirmSelectCalls.incrementAndGet();
                            yield null;
                        }
                        case "basicPublish" -> {
                            publishCalls.incrementAndGet();
                            yield null;
                        }
                        case "waitForConfirms" -> {
                            int index = confirmIndex.getAndIncrement();
                            yield confirmResults.get(Math.min(index, confirmResults.size() - 1));
                        }
                        case "close", "abort" -> null;
                        case "toString" -> "StubChannel";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == double.class) {
                return 0.0d;
            }
            if (returnType == float.class) {
                return 0.0f;
            }
            return null;
        }
    }

    private static final class StubConnectionFactory extends ConnectionFactory {
        private final Connection connection;
        private final AtomicInteger creates = new AtomicInteger();

        private StubConnectionFactory(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection newConnection() throws IOException, TimeoutException {
            creates.incrementAndGet();
            return connection;
        }
    }
}
