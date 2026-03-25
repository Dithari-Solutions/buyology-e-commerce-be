package com.buyology.ecommerce.courier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Used as the "data" JSON part of PATCH /api/admin/couriers/{id}.
 * All fields are optional — only non-null values are applied by the courier service.
 */
public record UpdateCourierRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Email @Size(max = 150) String email,
        VehicleType vehicleType
) {}
