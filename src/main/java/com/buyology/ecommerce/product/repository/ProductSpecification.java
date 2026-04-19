package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductSpecGroup;
import com.buyology.ecommerce.product.domain.ProductSpecOption;
import com.buyology.ecommerce.product.dto.ProductFilterRequest;
import com.buyology.ecommerce.store.domain.StoreProduct;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    private ProductSpecification() {
    }

    /**
     * Builds a JPA Specification from a {@link ProductFilterRequest}.
     * All predicates are combined with AND; null/blank filter fields are ignored.
     * Only ACTIVE (non-deleted) products are returned.
     */
    public static Specification<Product> from(ProductFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always exclude deleted products on the public search
            predicates.add(cb.notEqual(root.get("status"), "DELETED"));

            // Condition: NEW = isRefurbished false, REFURBISHED = true
            if (filter.getCondition() != null && !filter.getCondition().isBlank()) {
                boolean isRefurbished = "REFURBISHED".equalsIgnoreCase(filter.getCondition());
                predicates.add(cb.equal(root.get("isRefurbished"), isRefurbished));
            }

            // Brand
            if (filter.getBrandId() != null) {
                predicates.add(cb.equal(root.get("brand").get("id"), filter.getBrandId()));
            }

            // Availability status
            if (filter.getAvailabilityStatus() != null && !filter.getAvailabilityStatus().isBlank()) {
                try {
                    Product.AvailabilityStatus status = Product.AvailabilityStatus.valueOf(filter.getAvailabilityStatus());
                    predicates.add(cb.equal(root.get("availabilityStatus"), status));
                } catch (IllegalArgumentException ignored) {
                    // unknown value — skip predicate
                }
            }

            // Category
            if (filter.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.getCategoryId()));
            }

            // Super deal / limited stock flags
            if (Boolean.TRUE.equals(filter.getIsSuperDeal())) {
                predicates.add(cb.isTrue(root.get("isSuperDeal")));
            }
            if (Boolean.TRUE.equals(filter.getIsLimitedStock())) {
                predicates.add(cb.isTrue(root.get("isLimitedStock")));
            }

            // Price range — EXISTS subquery on store_products (price lives there, not on product)
            if (filter.getMinPrice() != null || filter.getMaxPrice() != null) {
                Subquery<Integer> priceSub = query.subquery(Integer.class);
                Root<StoreProduct> spRoot = priceSub.from(StoreProduct.class);
                List<Predicate> pricePredicates = new ArrayList<>();
                pricePredicates.add(cb.equal(spRoot.get("product"), root));
                pricePredicates.add(cb.isTrue(spRoot.get("isActive")));
                pricePredicates.add(cb.isNull(spRoot.get("deletedAt")));
                if (filter.getMinPrice() != null) {
                    pricePredicates.add(cb.greaterThanOrEqualTo(spRoot.get("storePrice"), filter.getMinPrice()));
                }
                if (filter.getMaxPrice() != null) {
                    pricePredicates.add(cb.lessThanOrEqualTo(spRoot.get("storePrice"), filter.getMaxPrice()));
                }
                priceSub.select(cb.literal(1)).where(pricePredicates.toArray(new Predicate[0]));
                predicates.add(cb.exists(priceSub));
            }

            // Spec-based filters — each uses an EXISTS subquery on product_spec_groups + product_spec_options
            addSpecPredicate(root, query, cb, predicates, "ram", filter.getRam());
            addSpecPredicate(root, query, cb, predicates, "storage", filter.getStorage());
            addSpecPredicate(root, query, cb, predicates, "processor", filter.getProcessor());
            addSpecPredicate(root, query, cb, predicates, "screen_size", filter.getScreenSize());
            addSpecPredicate(root, query, cb, predicates, "touchable_screen", filter.getTouchableScreen());
            addSpecPredicate(root, query, cb, predicates, "operating_system", filter.getOperatingSystem());
            addSpecPredicate(root, query, cb, predicates, "keyboard_language", filter.getKeyboardLanguage());

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Adds an EXISTS subquery predicate:
     * EXISTS (
     *   SELECT 1 FROM product_spec_groups g
     *   JOIN product_spec_options o ON o.group_id = g.id
     *   WHERE g.product_id = p.id AND g.code = :groupCode AND o.value = :optionValue
     * )
     */
    /**
     * ProductSpecOption has a ManyToOne "group" → ProductSpecGroup which has "product".
     * The subquery navigates: option.group.product = product AND option.group.code = groupCode
     * AND option.value = optionValue.
     */
    private static void addSpecPredicate(
            Root<Product> root,
            AbstractQuery<?> query,
            CriteriaBuilder cb,
            List<Predicate> predicates,
            String groupCode,
            String optionValue) {

        if (optionValue == null || optionValue.isBlank()) return;

        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<ProductSpecOption> optRoot = sub.from(ProductSpecOption.class);
        Join<ProductSpecOption, ProductSpecGroup> groupJoin = optRoot.join("group");

        sub.select(cb.literal(1))
                .where(
                        cb.equal(groupJoin.get("product"), root),
                        cb.equal(groupJoin.get("code"), groupCode),
                        cb.equal(optRoot.get("value"), optionValue));

        predicates.add(cb.exists(sub));
    }
}
