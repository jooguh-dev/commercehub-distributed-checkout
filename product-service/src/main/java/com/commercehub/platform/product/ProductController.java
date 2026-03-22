package com.commercehub.platform.product;

import com.commercehub.platform.shared.CreateProductRequest;
import com.commercehub.platform.shared.CreateProductResponse;
import com.commercehub.platform.shared.ProductDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {
    private final ProductApiService productApiService;

    public ProductController(ProductApiService productApiService) {
        this.productApiService = productApiService;
    }

    @PostMapping("/product")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductDto product = productApiService.create(request);
        return new CreateProductResponse(product.productId());
    }

    @GetMapping("/products/{productId}")
    public ProductDto getProduct(@PathVariable("productId") int productId) {
        return productApiService.get(productId);
    }
}
