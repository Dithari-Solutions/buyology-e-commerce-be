package com.buyology.ecommerce.banner.domain;

import java.util.UUID;

public class BannerNotFoundException extends RuntimeException {
    public BannerNotFoundException(UUID id) {
        super("Banner not found with id: " + id);
    }
}
