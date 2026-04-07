package com.buyology.ecommerce.order.domain.converter;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

/**
 * Handles legacy mapping for DeliveryMethod enum.
 * Maps 'EXPRESS_DELIVERY' -> 'EXPRESS'
 * Maps 'REGULAR_ORDER'    -> 'REGULAR'
 */
@Converter(autoApply = true)
public class DeliveryMethodConverter implements AttributeConverter<DeliveryMethod, String> {

    private static final Logger log = LoggerFactory.getLogger(DeliveryMethodConverter.class);

    @Override
    public String convertToDatabaseColumn(DeliveryMethod attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public DeliveryMethod convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        // Legacy mapping
        if ("EXPRESS_DELIVERY".equalsIgnoreCase(dbData)) {
            return DeliveryMethod.EXPRESS;
        }
        if ("REGULAR_ORDER".equalsIgnoreCase(dbData)) {
            return DeliveryMethod.REGULAR;
        }

        try {
            return DeliveryMethod.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[CONVERTER] Unknown delivery method in DB: {}. Defaulting to null.", dbData);
            return null;
        }
    }
}
