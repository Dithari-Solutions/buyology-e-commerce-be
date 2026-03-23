package com.buyology.ecommerce.courier.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreateCourierRequest(

        // Personal details
        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @NotBlank @Size(max = 30)
        String phone,

        @Email @Size(max = 150)
        String email,

        @org.hibernate.validator.constraints.URL @Size(max = 2048)
        String profileImageUrl,

        // Auth
        @NotBlank @Size(min = 8, max = 100)
        String initialPassword,

        // Vehicle
        @NotNull
        VehicleType vehicleType,

        @Size(max = 100) String vehicleMake,
        @Size(max = 100) String vehicleModel,
        @Min(1900) @Max(2100) Integer vehicleYear,
        @Size(max = 50)  String vehicleColor,
        @Size(max = 50)  String licensePlate,

        @org.hibernate.validator.constraints.URL @Size(max = 2048)
        String vehicleRegistrationUrl,

        // Driving licence — required only for SCOOTER and CAR
        @Size(max = 100) String drivingLicenseNumber,
        LocalDate drivingLicenseExpiry,

        @org.hibernate.validator.constraints.URL @Size(max = 2048)
        String drivingLicenseFrontUrl,

        @org.hibernate.validator.constraints.URL @Size(max = 2048)
        String drivingLicenseBackUrl
) {}
