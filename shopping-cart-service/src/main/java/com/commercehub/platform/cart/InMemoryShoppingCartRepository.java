package com.commercehub.platform.cart;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Primary
@ConditionalOnProperty(name = "cart.storage.mode", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryShoppingCartRepository implements ShoppingCartRepository {
    private final Map<String, ShoppingCart> carts = new ConcurrentHashMap<>();

    @Override
    public ShoppingCart create(int customerId) {
        String id = UUID.randomUUID().toString();
        ShoppingCart cart = new ShoppingCart(id, customerId);
        carts.put(id, cart.copy());
        return cart;
    }

    @Override
    public Optional<ShoppingCart> findById(String shoppingCartId) {
        ShoppingCart cart = carts.get(shoppingCartId);
        return cart == null ? Optional.empty() : Optional.of(cart.copy());
    }

    @Override
    public ShoppingCart save(ShoppingCart shoppingCart) {
        ShoppingCart persisted = carts.compute(shoppingCart.shoppingCartId(), (cartId, existing) -> {
            if (existing == null) {
                throw new ShoppingCartNotFoundException(cartId);
            }

            if (existing.version() != shoppingCart.version()) {
                throw new ConcurrentCartModificationException(cartId);
            }

            ShoppingCart updated = shoppingCart.copy();
            updated.markPersisted(existing.version() + 1);
            return updated;
        });
        shoppingCart.markPersisted(persisted.version());
        return persisted.copy();
    }

    @Override
    public String nextOrderId() {
        return UUID.randomUUID().toString();
    }
}
