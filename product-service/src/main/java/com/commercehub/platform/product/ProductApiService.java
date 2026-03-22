package com.commercehub.platform.product;

import com.commercehub.platform.shared.CreateProductRequest;
import com.commercehub.platform.shared.ProductDto;
import org.springframework.stereotype.Service;

@Service
public class ProductApiService {
    private final ProductRepository productRepository;
    private final ProductFailureModeProperties failureModeProperties;
    private final FailureDecisionSource failureDecisionSource;

    public ProductApiService(ProductRepository productRepository,
                             ProductFailureModeProperties failureModeProperties,
                             FailureDecisionSource failureDecisionSource) {
        this.productRepository = productRepository;
        this.failureModeProperties = failureModeProperties;
        this.failureDecisionSource = failureDecisionSource;
    }

    public ProductDto create(CreateProductRequest request) {
        return productRepository.save(
                request.sku(),
                request.manufacturer(),
                request.categoryId(),
                request.weight(),
                request.someOtherId()
        );
    }

    public ProductDto get(int productId) {
        if (failureModeProperties.enabled() && failureDecisionSource.shouldFail()) {
            throw new ProductTemporarilyUnavailableException();
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
