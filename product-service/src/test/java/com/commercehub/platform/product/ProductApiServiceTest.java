package com.commercehub.platform.product;

import com.commercehub.platform.shared.CreateProductRequest;
import com.commercehub.platform.shared.ProductDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductApiServiceTest {
    @Mock
    private FailureDecisionSource failureDecisionSource;

    @Test
    void exposesSeededProductsAcrossInstances() {
        ProductApiService service = new ProductApiService(
                new ProductRepository(),
                new ProductFailureModeProperties(false),
                failureDecisionSource
        );

        ProductDto product = service.get(1);

        assertEquals(1, product.productId());
        assertEquals("PT-001", product.sku());
        assertEquals("Acme", product.manufacturer());
    }

    @Test
    void createsAndReadsProductsFromRepository() {
        ProductApiService service = new ProductApiService(
                new ProductRepository(),
                new ProductFailureModeProperties(false),
                failureDecisionSource
        );

        ProductDto product = service.create(new CreateProductRequest("ABC123XYZ9", "Acme", 5, 100, 88));

        assertEquals(4, product.productId());
        assertEquals(product, service.get(product.productId()));
    }

    @Test
    void throwsWhenProductMissing() {
        ProductApiService service = new ProductApiService(
                new ProductRepository(),
                new ProductFailureModeProperties(false),
                failureDecisionSource
        );

        assertThrows(ProductNotFoundException.class, () -> service.get(999));
    }

    @Test
    void returnsServiceUnavailableWhenBadInstanceFails() {
        when(failureDecisionSource.shouldFail()).thenReturn(true);
        ProductApiService service = new ProductApiService(
                new ProductRepository(),
                new ProductFailureModeProperties(true),
                failureDecisionSource
        );

        assertThrows(ProductTemporarilyUnavailableException.class, () -> service.get(1));
    }
}
