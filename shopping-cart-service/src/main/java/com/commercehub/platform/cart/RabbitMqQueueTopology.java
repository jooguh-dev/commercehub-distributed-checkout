package com.commercehub.platform.cart;

import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.util.Map;

final class RabbitMqQueueTopology {
    private RabbitMqQueueTopology() {
    }

    static void declare(Channel channel,
                        String queueName,
                        String deadLetterExchange,
                        String deadLetterQueue,
                        String deadLetterRoutingKey) throws IOException {
        channel.exchangeDeclare(deadLetterExchange, "direct", true);
        channel.queueDeclare(deadLetterQueue, true, false, false, null);
        channel.queueBind(deadLetterQueue, deadLetterExchange, deadLetterRoutingKey);
        channel.queueDeclare(queueName, true, false, false, Map.of(
                "x-dead-letter-exchange", deadLetterExchange,
                "x-dead-letter-routing-key", deadLetterRoutingKey
        ));
    }
}
