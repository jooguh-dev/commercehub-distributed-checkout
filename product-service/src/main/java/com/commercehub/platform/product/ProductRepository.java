package com.commercehub.platform.product;

import com.commercehub.platform.shared.ProductDto;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class ProductRepository {
    private static final List<ProductDto> DEFAULT_CATALOG = List.of(
            new ProductDto(1, "PT-001", "Acme", 10, 2, 77),
            new ProductDto(2, "BK-201", "Globex", 20, 1, 88),
            new ProductDto(3, "EL-450", "Initech", 30, 5, 99)
    );

    private final AtomicInteger idGenerator = new AtomicInteger(DEFAULT_CATALOG.size());
    private final Map<Integer, ProductDto> products = new ConcurrentHashMap<>();

    public ProductRepository() {
        for (ProductDto product : DEFAULT_CATALOG) {
            products.put(product.productId(), product);
        }
    }

    public ProductDto save(String sku, String manufacturer, int categoryId, int weight, int someOtherId) {
        int productId = idGenerator.incrementAndGet();
        ProductDto product = new ProductDto(productId, sku, manufacturer, categoryId, weight, someOtherId);
        products.put(productId, product);
        return product;
    }

    public Optional<ProductDto> findById(int productId) {
        return Optional.ofNullable(products.get(productId));
    }
}
