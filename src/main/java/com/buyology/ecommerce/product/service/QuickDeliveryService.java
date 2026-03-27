package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QuickDeliveryService {

    /**
     * Approximate delivery radius for a 30-minute window.
     * Assumes average urban delivery speed of 25 km/h → 12.5 km.
     */
    private static final double THIRTY_MIN_RADIUS_KM = 12.5;

    private final StoreLocationRepository storeLocationRepository;
    private final StoreProductRepository storeProductRepository;
    private final ProductService productService;

    public QuickDeliveryService(StoreLocationRepository storeLocationRepository,
                                StoreProductRepository storeProductRepository,
                                ProductService productService) {
        this.storeLocationRepository = storeLocationRepository;
        this.storeProductRepository = storeProductRepository;
        this.productService = productService;
    }

    /**
     * Finds all products that can be delivered within 30 minutes to the given location.
     *
     * <ol>
     *   <li>Queries distinct store IDs within the 30-min delivery radius via Haversine.</li>
     *   <li>Fetches all active products linked to those stores.</li>
     *   <li>Returns them as a full {@link ProductResponse} list in the requested language.</li>
     * </ol>
     */
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getQuickDeliveryProducts(
            double userLat, double userLng, String lang, String countryCode, String currency) {

        List<UUID> nearbyStoreIds = storeLocationRepository
                .findStoreIdsWithinRadius(userLat, userLng, THIRTY_MIN_RADIUS_KM);

        if (nearbyStoreIds.isEmpty()) {
            return ApiResponse.success(List.of(), "No stores available for 30-min delivery in your area");
        }

        List<UUID> productIds = storeProductRepository
                .findActiveProductsByStoreIds(nearbyStoreIds)
                .stream()
                .map(p -> p.getId())
                .toList();

        return productService.getProductsByIds(productIds, lang, countryCode, currency);
    }
}
