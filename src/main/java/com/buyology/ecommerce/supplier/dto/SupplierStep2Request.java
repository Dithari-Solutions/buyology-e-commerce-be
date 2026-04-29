package com.buyology.ecommerce.supplier.dto;

import com.buyology.ecommerce.supplier.domain.SupplierApplication.InitialListingRange;
import com.buyology.ecommerce.supplier.domain.SupplierApplication.ProductCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class SupplierStep2Request {

    @NotNull
    private UUID applicationId;

    private List<String> productCategories;

    @Size(max = 500)
    private String mainBrands;

    private ProductCondition productCondition;

    private InitialListingRange initialListingRange;

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public List<String> getProductCategories() { return productCategories; }
    public void setProductCategories(List<String> productCategories) { this.productCategories = productCategories; }
    public String getMainBrands() { return mainBrands; }
    public void setMainBrands(String mainBrands) { this.mainBrands = mainBrands; }
    public ProductCondition getProductCondition() { return productCondition; }
    public void setProductCondition(ProductCondition productCondition) { this.productCondition = productCondition; }
    public InitialListingRange getInitialListingRange() { return initialListingRange; }
    public void setInitialListingRange(InitialListingRange initialListingRange) { this.initialListingRange = initialListingRange; }
}
