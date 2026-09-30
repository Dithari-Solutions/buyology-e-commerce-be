package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.dto.FlashSaleRemoveRequest;
import com.buyology.ecommerce.store.dto.FlashSaleRequest;
import com.buyology.ecommerce.store.dto.StoreProductResponse;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The flash sale: a batch of products discounted until a date.
 *
 * <p>It is not a second pricing system. Everything here writes the four columns that already price
 * this shop — {@code discount_type}, {@code discount_value} and, since V60, the two dates that say
 * when they apply — so a flash-sale price reaches the customer through the exact same
 * {@code StoreProduct.effectivePrice} the cart stamps, the checkout re-prices against and Buy Now
 * charges. There is nothing to keep in step, and nothing to run: a sale ends because the data says it
 * has, not because a job noticed.
 *
 * <p>Every write here is ALL-OR-NOTHING. An admin putting forty products on sale and getting a 400
 * with seventeen of them already discounted is worse than an outright refusal, because the seventeen
 * are now live at prices nobody reviewed. So the whole batch is resolved and validated before the
 * first row is touched, inside one transaction.
 */
@Service
public class FlashSaleService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleService.class);

    private final StoreProductRepository storeProductRepository;
    private final StoreProductService storeProductService;

    public FlashSaleService(StoreProductRepository storeProductRepository,
                            StoreProductService storeProductService) {
        this.storeProductRepository = storeProductRepository;
        this.storeProductService = storeProductService;
    }

    // ── Put products on the flash sale ────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<List<StoreProductResponse>>> putOnFlashSale(FlashSaleRequest request) {
        // One instant for the whole call: the validation that says "this end date is not in the past"
        // and the window that gets stored must be judged against the same moment, or a batch big
        // enough to straddle a second could store something it just refused.
        Instant now = Instant.now();

        Instant batchStart = resolveStart(request.getStartsOn(), request.getStartsAt());
        Instant batchEnd = resolveEnd(request.getEndsOn(), request.getEndsAt());

        // Resolve and validate EVERYTHING first — see the class comment.
        List<StoreProduct> listings = new ArrayList<>(request.getItems().size());
        for (FlashSaleRequest.FlashSaleItemRequest item : request.getItems()) {
            listings.add(resolve(item));
        }

        List<Planned> planned = new ArrayList<>(request.getItems().size());
        for (int i = 0; i < request.getItems().size(); i++) {
            FlashSaleRequest.FlashSaleItemRequest item = request.getItems().get(i);
            StoreProduct sp = listings.get(i);

            // The type and the value are ONE decision, so they are overridden together or not at all.
            // Resolved independently, an item that overrides only the type inherits the batch's value
            // and means something nobody typed: batch "PERCENTAGE 20" plus {"discountType":"FIXED"}
            // became FIXED 20 — a 2,000 AED laptop on sale at 20 AED, and validateDiscount waves it
            // through because 20 is indeed below 2000. Taking the pair together turns that into the
            // "discountValue is required when discountType is set" refusal it always should have been.
            Product.DiscountType discountType;
            BigDecimal discountValue;
            if (item.getDiscountType() != null || item.getDiscountValue() != null) {
                discountType = item.getDiscountType();
                discountValue = item.getDiscountValue();
            } else {
                discountType = request.getDiscountType();
                discountValue = request.getDiscountValue();
            }
            Instant endsAt = resolveEnd(item.getEndsOn(), item.getEndsAt());
            if (endsAt == null) endsAt = batchEnd;

            FlashSalePolicy.requireEnd(endsAt);
            FlashSalePolicy.validateDiscount(discountType, discountValue, sp.getStorePrice());
            FlashSalePolicy.validateWindow(batchStart, endsAt, now);
            // A variant-bearing listing used to be refused outright here: its variant lines would have
            // been charged store_product_variants.store_price undiscounted while the card advertised
            // this sale. Every line is now priced from the listing row this sale writes to, so a
            // campaign may include a product priced per variant — which is a large part of the
            // catalogue and was previously impossible to put on sale at all.

            planned.add(new Planned(sp, discountType, discountValue, endsAt));
        }

        List<StoreProduct> saved = new ArrayList<>(planned.size());
        for (Planned p : planned) {
            p.storeProduct().setDiscountType(p.discountType());
            p.storeProduct().setDiscountValue(p.discountValue());
            p.storeProduct().setDiscountStartsAt(batchStart);
            p.storeProduct().setDiscountEndsAt(p.endsAt());
            saved.add(storeProductRepository.save(p.storeProduct()));
        }
        List<StoreProductResponse> responses = storeProductService.toResponseBatch(saved, now);

        log.info("[FLASH SALE] {} store product(s) on sale, starts={} ends={}",
                responses.size(), batchStart, batchEnd);
        return ApiResponse.success(responses,
                responses.size() + " product(s) put on the flash sale");
    }

    // ── What is on the flash sale ─────────────────────────────────────────────

    /** The most rows one request may ask for, so a bad {@code size} cannot become the old unbounded query. */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * Live AND scheduled sales, soonest-ending first. Ended ones are left out: the price has already
     * reverted, so listing them would invite an admin to "end" a sale that is over.
     *
     * <p>Paged, and bounded even when the caller sends nothing: the first version selected every
     * discounted row in the shop and then built a response per row, each of which fetched its title
     * and its variants — one query per row, on an admin screen with no upper size. The message names
     * the total so a screen showing the first page can say what it is not showing.
     */
    public ResponseEntity<ApiResponse<List<StoreProductResponse>>> getFlashSale(UUID storeId, int page, int size) {
        Instant now = Instant.now();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_PAGE_SIZE));

        List<StoreProduct> rows;
        long total;
        if (storeId != null) {
            rows = storeProductRepository.findFlashSaleAssignmentsByStore(storeId, now, pageable);
            total = storeProductRepository.countFlashSaleAssignmentsByStore(storeId, now);
        } else {
            rows = storeProductRepository.findFlashSaleAssignments(now, pageable);
            total = storeProductRepository.countFlashSaleAssignments(now);
        }

        List<StoreProductResponse> responses = storeProductService.toResponseBatch(rows, now);
        return ApiResponse.success(responses,
                "Flash sale fetched successfully (" + responses.size() + " of " + total + ")");
    }

    // ── Take products off the flash sale ──────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<List<StoreProductResponse>>> removeFromFlashSale(FlashSaleRemoveRequest request) {
        Instant now = Instant.now();
        List<StoreProduct> rows = new ArrayList<>(request.getStoreProductIds().size());
        for (UUID id : request.getStoreProductIds()) {
            rows.add(storeProductRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Store product not found: " + id)));
        }
        List<StoreProduct> cleared = new ArrayList<>(rows.size());
        for (StoreProduct sp : rows) {
            clear(sp);
            cleared.add(storeProductRepository.save(sp));
        }
        List<StoreProductResponse> responses = storeProductService.toResponseBatch(cleared, now);
        log.info("[FLASH SALE] {} store product(s) taken off sale", responses.size());
        return ApiResponse.success(responses, responses.size() + " product(s) taken off the flash sale");
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoreProductResponse>> removeOneFromFlashSale(UUID storeProductId) {
        StoreProduct sp = storeProductRepository.findById(storeProductId)
                .orElseThrow(() -> new IllegalArgumentException("Store product not found: " + storeProductId));
        clear(sp);
        return ApiResponse.success(storeProductService.toResponse(storeProductRepository.save(sp), Instant.now()),
                "Product taken off the flash sale");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private record Planned(StoreProduct storeProduct, Product.DiscountType discountType,
                           BigDecimal discountValue, Instant endsAt) {
    }

    /**
     * The sale price goes away and so does the window. Leaving the dates behind on a cleared
     * discount would leave a row that the flash-sale listing still matches on and that a later
     * "25% off" would silently inherit an expiry from.
     */
    private void clear(StoreProduct sp) {
        sp.setDiscountType(null);
        sp.setDiscountValue(null);
        sp.setDiscountStartsAt(null);
        sp.setDiscountEndsAt(null);
    }

    private StoreProduct resolve(FlashSaleRequest.FlashSaleItemRequest item) {
        StoreProduct sp;
        if (item.getStoreProductId() != null) {
            sp = storeProductRepository.findById(item.getStoreProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Store product not found: " + item.getStoreProductId()));
        } else if (item.getStoreId() != null && item.getProductId() != null) {
            sp = storeProductRepository.findByStore_IdAndProduct_Id(item.getStoreId(), item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product " + item.getProductId()
                            + " is not assigned to store " + item.getStoreId()));
        } else {
            throw new IllegalArgumentException(
                    "Each item needs either storeProductId, or both storeId and productId");
        }
        // A removed assignment is invisible to every storefront query, so discounting it would put a
        // product on a sale no customer can see and no cart can reach.
        if (sp.getDeletedAt() != null) {
            throw new IllegalArgumentException("Store product " + sp.getId()
                    + " has been removed from its store — re-assign it before putting it on sale");
        }
        return sp;
    }

    /** The instant wins over the calendar date: it is the more specific of the two. */
    private Instant resolveStart(LocalDate startsOn, Instant startsAt) {
        if (startsAt != null) return startsAt;
        return startsOn != null ? FlashSalePolicy.startOfDayInBusinessZone(startsOn) : null;
    }

    private Instant resolveEnd(LocalDate endsOn, Instant endsAt) {
        if (endsAt != null) return endsAt;
        return endsOn != null ? FlashSalePolicy.endOfDayInBusinessZone(endsOn) : null;
    }
}
