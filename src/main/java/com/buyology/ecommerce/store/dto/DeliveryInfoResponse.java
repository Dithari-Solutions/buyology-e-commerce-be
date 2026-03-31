package com.buyology.ecommerce.store.dto;

import java.util.List;
import java.util.UUID;

public class DeliveryInfoResponse {

    private List<String> cities;
    private List<StoreDeliveryInfo> stores;

    public List<String> getCities() { return cities; }
    public void setCities(List<String> cities) { this.cities = cities; }

    public List<StoreDeliveryInfo> getStores() { return stores; }
    public void setStores(List<StoreDeliveryInfo> stores) { this.stores = stores; }

    public static class StoreDeliveryInfo {
        private UUID locationId;
        private UUID storeId;
        private String branchName;
        private String city;
        private String address;
        private Double latitude;
        private Double longitude;
        private Double distanceKm;
        private boolean expressDelivery; // true = ≤30 min

        public UUID getLocationId() { return locationId; }
        public void setLocationId(UUID locationId) { this.locationId = locationId; }

        public UUID getStoreId() { return storeId; }
        public void setStoreId(UUID storeId) { this.storeId = storeId; }

        public String getBranchName() { return branchName; }
        public void setBranchName(String branchName) { this.branchName = branchName; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }

        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }

        public Double getDistanceKm() { return distanceKm; }
        public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

        public boolean isExpressDelivery() { return expressDelivery; }
        public void setExpressDelivery(boolean expressDelivery) { this.expressDelivery = expressDelivery; }
    }
}
