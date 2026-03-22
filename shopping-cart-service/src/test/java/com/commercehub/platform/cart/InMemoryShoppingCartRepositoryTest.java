package com.commercehub.platform.cart;

import com.commercehub.platform.shared.CartItemDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryShoppingCartRepositoryTest {

    @Test
    void saveRejectsStaleVersion() {
        InMemoryShoppingCartRepository repository = new InMemoryShoppingCartRepository();
        ShoppingCart created = repository.create(7);

        ShoppingCart firstRead = repository.findById(created.shoppingCartId()).orElseThrow();
        ShoppingCart secondRead = repository.findById(created.shoppingCartId()).orElseThrow();

        firstRead.addItem(new CartItemDto(1, 2));
        repository.save(firstRead);

        secondRead.addItem(new CartItemDto(2, 1));

        assertThrows(ConcurrentCartModificationException.class, () -> repository.save(secondRead));
        assertEquals(1, repository.findById(created.shoppingCartId()).orElseThrow().items().size());
    }
}
