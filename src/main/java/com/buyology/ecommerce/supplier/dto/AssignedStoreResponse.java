package com.buyology.ecommerce.supplier.dto;

import java.util.UUID;

public record AssignedStoreResponse(UUID id, String name, String slug, String status) {
}
