package com.buyology.ecommerce.store.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Take a batch of products off the flash sale.
 *
 * <p>Removal clears the discount AND its window, so nothing is left behind to expire later or to
 * come back to life if the listing is re-assigned. Ending a sale early and never having run one look
 * identical afterwards, which is the point: there is no third state to reason about.
 */
@Schema(description = "Take store products off the flash sale — clears the discount and its window")
public class FlashSaleRemoveRequest {

    @NotEmpty(message = "storeProductIds must contain at least one id")
    @Size(max = 500, message = "A single call may take at most 500 products off the flash sale")
    @ArraySchema(schema = @Schema(description = "store_products ids to clear"))
    private List<UUID> storeProductIds;

    public List<UUID> getStoreProductIds() { return storeProductIds; }
    public void setStoreProductIds(List<UUID> storeProductIds) { this.storeProductIds = storeProductIds; }
}
