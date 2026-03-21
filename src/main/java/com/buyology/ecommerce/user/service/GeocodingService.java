package com.buyology.ecommerce.user.service;

import com.buyology.ecommerce.user.config.GoogleMapsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Validates a delivery address against the Google Maps Geocoding API.
 * Returns a GeocodingResult with the canonical formatted address and
 * lat/lng coordinates if the address resolves to a real location.
 * Throws IllegalArgumentException if the address cannot be found.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final String GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    private final RestTemplate restTemplate;
    private final GoogleMapsProperties googleMapsProperties;
    private final ObjectMapper objectMapper;

    public GeocodingService(RestTemplate restTemplate,
                            GoogleMapsProperties googleMapsProperties,
                            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.googleMapsProperties = googleMapsProperties;
        this.objectMapper = objectMapper;
    }

    public record GeocodingResult(String formattedAddress, double latitude, double longitude) {}

    /**
     * Builds a single address string from components, calls Google Geocoding,
     * and returns the standardised result.
     *
     * @throws IllegalArgumentException if the address cannot be geocoded
     */
    public GeocodingResult validateAndGeocode(String addressLine1, String addressLine2,
                                               String city, String state,
                                               String country, String postalCode) {
        String rawAddress = buildAddressString(addressLine1, addressLine2, city, state, country, postalCode);

        String url = UriComponentsBuilder.fromUriString(GEOCODING_URL)
                .queryParam("address", rawAddress)
                .queryParam("key", googleMapsProperties.getApiKey())
                .build()
                .toUriString();

        try {
            String responseBody = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(responseBody);

            String status = root.get("status").asText();

            if (!"OK".equals(status)) {
                log.warn("Geocoding returned status '{}' for address: {}", status, rawAddress);
                throw new IllegalArgumentException(
                        "Address could not be verified. Please check and re-enter the address details.");
            }

            JsonNode firstResult = root.get("results").get(0);
            String formattedAddress = firstResult.get("formatted_address").asText();
            JsonNode location = firstResult.get("geometry").get("location");
            double lat = location.get("lat").asDouble();
            double lng = location.get("lng").asDouble();

            log.info("Address geocoded successfully: {}", formattedAddress);
            return new GeocodingResult(formattedAddress, lat, lng);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Geocoding API call failed for address '{}': {}", rawAddress, e.getMessage());
            throw new RuntimeException("Address verification service is temporarily unavailable. Please try again.", e);
        }
    }

    private String buildAddressString(String line1, String line2, String city,
                                       String state, String country, String postalCode) {
        StringBuilder sb = new StringBuilder();
        if (line1 != null && !line1.isBlank()) sb.append(line1).append(", ");
        if (line2 != null && !line2.isBlank()) sb.append(line2).append(", ");
        if (city != null && !city.isBlank()) sb.append(city).append(", ");
        if (state != null && !state.isBlank()) sb.append(state).append(", ");
        if (postalCode != null && !postalCode.isBlank()) sb.append(postalCode).append(", ");
        if (country != null && !country.isBlank()) sb.append(country);
        return sb.toString().replaceAll(",\\s*$", "").trim();
    }
}
