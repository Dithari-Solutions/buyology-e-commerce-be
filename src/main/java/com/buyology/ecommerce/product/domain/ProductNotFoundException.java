package com.buyology.ecommerce.product.domain;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("Product not found with id: " + id);
    }

    public ProductNotFoundException(String message) {
        super(message);
    }
}
