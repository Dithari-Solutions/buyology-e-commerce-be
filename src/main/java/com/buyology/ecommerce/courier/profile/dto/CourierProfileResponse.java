package com.buyology.ecommerce.courier.profile.dto;

import com.buyology.ecommerce.courier.profile.domain.CourierProfile;

import java.time.Instant;
import java.util.UUID;

public record CourierProfileResponse(
        UUID id,
        UUID storeId,
        String firstName,
        String lastName,
        String phone,
        String email,
        String vehicleType,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static CourierProfileResponse from(CourierProfile c) {
        return new CourierProfileResponse(
                c.getId(), c.getStoreId(), c.getFirstName(), c.getLastName(),
                c.getPhone(), c.getEmail(), c.getVehicleType(), c.isActive(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
