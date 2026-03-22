package com.commercehub.platform.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WarehouseMessageProcessorTest {

    @Test
    void deserializesAndRecordsCheckoutMessages() throws Exception {
        WarehouseStatistics statistics = new WarehouseStatistics();
        WarehouseMessageProcessor processor = new WarehouseMessageProcessor(new ObjectMapper(), statistics);

        processor.process("""
                {"orderId":"order-1","shoppingCartId":"cart-2","customerId":3,"items":[{"productId":11,"quantity":4},{"productId":12,"quantity":2}]}
                """.getBytes());

        assertEquals(1L, statistics.totalOrders());
        assertEquals(4L, statistics.quantityForProduct(11));
        assertEquals(2L, statistics.quantityForProduct(12));
    }

    @Test
    void ignoresDuplicateMessagesForSameOrderId() throws Exception {
        WarehouseStatistics statistics = new WarehouseStatistics();
        WarehouseMessageProcessor processor = new WarehouseMessageProcessor(new ObjectMapper(), statistics);

        byte[] body = """
                {"orderId":"order-1","shoppingCartId":"cart-2","customerId":3,"items":[{"productId":11,"quantity":4}]}
                """.getBytes();

        processor.process(body);
        processor.process(body);

        assertEquals(1L, statistics.totalOrders());
        assertEquals(4L, statistics.quantityForProduct(11));
    }

    @Test
    void rejectsInvalidMessagesWithoutRecording() {
        WarehouseStatistics statistics = new WarehouseStatistics();
        WarehouseMessageProcessor processor = new WarehouseMessageProcessor(new ObjectMapper(), statistics);

        assertThrows(InvalidWarehouseMessageException.class, () -> processor.process("not-json".getBytes()));
        assertEquals(0L, statistics.totalOrders());
    }
}
