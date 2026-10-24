package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.common.enums.SpecUnit;
import com.buyology.ecommerce.product.domain.ProductCategoryTranslation;
import com.buyology.ecommerce.product.domain.SpecCode;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository;
import com.buyology.ecommerce.product.repository.SpecCodeRepository;
import com.buyology.ecommerce.product.service.ProductService;
import com.buyology.ecommerce.warranty.dto.WarrantySpecLine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a warranty card says about the product: English name, brand, category, condition and its
 * main specs, for the exact variant that was sold.
 *
 * <p>Read through the admin product view rather than the tables, so the card shows the same names
 * and values an admin sees in the dashboard. English only, because Dithari's checker is.
 */
@Component
public class WarrantyProductSnapshotter {

    /** A card is a summary, not a spec sheet. */
    static final int MAX_SPECS = 6;

    private final ProductService productService;
    private final ProductCategoryTranslationRepository categoryTranslations;
    private final SpecCodeRepository specCodes;

    public WarrantyProductSnapshotter(ProductService productService,
                                      ProductCategoryTranslationRepository categoryTranslations,
                                      SpecCodeRepository specCodes) {
        this.productService = productService;
        this.categoryTranslations = categoryTranslations;
        this.specCodes = specCodes;
    }

    public record Snapshot(String name, String brand, String category, String condition,
                           List<WarrantySpecLine> specs) {
    }

    public Snapshot snapshot(UUID productId, UUID variantId) {
        ProductResponse product = productService.getProductByIdAdmin(productId, "EN").getBody().getData();
        String category = product.getCategoryId() == null ? null : categoryTranslations
                .findByCategoryIdAndLanguage(product.getCategoryId(), "EN")
                .map(ProductCategoryTranslation::getName)
                .orElse(null);

        ProductResponse.VariantDto variant = variantId == null || product.getVariants() == null ? null
                : product.getVariants().stream().filter(v -> variantId.equals(v.getId())).findFirst().orElse(null);

        return new Snapshot(
                product.getTitle(),
                product.getBrandName(),
                category,
                condition(product.getIsRefurbished(), product.getRefurbGrade()),
                mainSpecs(product.getSpecs(), product.getColors(), variant, specOrder()));
    }

    /** The spec-code registry's order (RAM, storage, processor, ...), so every card reads alike. */
    private Map<String, Integer> specOrder() {
        Map<String, Integer> order = new HashMap<>();
        List<SpecCode> codes = specCodes.findAllByOrderByDisplayOrderAscCodeAsc();
        for (int i = 0; i < codes.size(); i++) {
            order.put(codes.get(i).getCode().toLowerCase(Locale.ROOT), i);
        }
        return order;
    }

    static String condition(Boolean refurbished, String grade) {
        if (!Boolean.TRUE.equals(refurbished)) return "New";
        return grade == null || grade.isBlank() ? "Refurbished" : "Refurbished · Grade " + grade;
    }

    /**
     * Registered spec codes first in registry order, anything else after in the product's own
     * order, capped at {@link #MAX_SPECS}. For a variant, a group the variant chose from shows only
     * the chosen option — the 32 GB unit's card must not say "16 GB / 32 GB".
     */
    static List<WarrantySpecLine> mainSpecs(List<ProductResponse.SpecGroupDto> groups,
                                            List<ProductResponse.ColorOptionDto> colors,
                                            ProductResponse.VariantDto variant,
                                            Map<String, Integer> registryOrder) {
        Set<UUID> chosen = variant == null || variant.getSpecOptionIds() == null
                ? Set.of() : Set.copyOf(variant.getSpecOptionIds());

        List<ProductResponse.SpecGroupDto> ordered = new ArrayList<>(groups == null ? List.of() : groups);
        // Stable sort: groups the registry does not know keep their relative order at the end.
        ordered.sort(Comparator.comparingInt(g -> registryOrder.getOrDefault(
                g.getCode() == null ? "" : g.getCode().toLowerCase(Locale.ROOT), Integer.MAX_VALUE)));

        List<WarrantySpecLine> lines = new ArrayList<>();
        for (ProductResponse.SpecGroupDto group : ordered) {
            if (lines.size() == MAX_SPECS) break;
            List<ProductResponse.SpecOptionDto> options = group.getOptions() == null ? List.of() : group.getOptions();
            List<ProductResponse.SpecOptionDto> picked = options.stream().filter(o -> chosen.contains(o.getId())).toList();
            String value = (picked.isEmpty() ? options : picked).stream()
                    .map(o -> formatValue(o.getValue(), o.getUnit()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(" / "));
            if (!value.isEmpty() && group.getName() != null && !group.getName().isBlank()) {
                lines.add(new WarrantySpecLine(group.getName(), value));
            }
        }

        // The colour is only knowable from the variant, or when the product comes in one colour:
        // order items do not record which colour swatch a shopper picked.
        String colour = colourOf(colors, chosen);
        if (colour != null && lines.size() < MAX_SPECS) {
            lines.add(new WarrantySpecLine("Colour", colour));
        }
        return lines;
    }

    private static String colourOf(List<ProductResponse.ColorOptionDto> colors, Set<UUID> chosen) {
        if (colors == null || colors.isEmpty()) return null;
        for (ProductResponse.ColorOptionDto c : colors) {
            if (chosen.contains(c.getId())) return blankToNull(c.getValue());
        }
        return colors.size() == 1 ? blankToNull(colors.get(0).getValue()) : null;
    }

    /** "16" + GB reads "16 GB"; 14 + INCH reads 14"; a value that already carries its unit is left alone. */
    static String formatValue(String value, SpecUnit unit) {
        String v = blankToNull(value);
        if (v == null || unit == null) return v;
        String label = switch (unit) {
            case INCH -> "\"";
            case HZ -> "Hz";
            case G -> "g";
            case KG -> "kg";
            default -> unit.name();
        };
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.endsWith(label.toLowerCase(Locale.ROOT)) || (unit == SpecUnit.INCH && lower.endsWith("inch"))) {
            return v;
        }
        return unit == SpecUnit.INCH ? v + label : v + " " + label;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
