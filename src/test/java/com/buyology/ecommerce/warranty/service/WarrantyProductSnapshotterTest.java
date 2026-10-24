package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.common.enums.SpecUnit;
import com.buyology.ecommerce.product.dto.ProductResponse.ColorOptionDto;
import com.buyology.ecommerce.product.dto.ProductResponse.SpecGroupDto;
import com.buyology.ecommerce.product.dto.ProductResponse.SpecOptionDto;
import com.buyology.ecommerce.product.dto.ProductResponse.VariantDto;
import com.buyology.ecommerce.warranty.dto.WarrantySpecLine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** What the card says about the product: which specs, in what order, and how they read. */
class WarrantyProductSnapshotterTest {

    private static final Map<String, Integer> REGISTRY = Map.of(
            "ram", 0, "storage", 1, "processor", 2, "screen_size", 4, "operating_system", 6);

    private static SpecOptionDto option(String value, SpecUnit unit) {
        return new SpecOptionDto(UUID.randomUUID(), value, unit);
    }

    private static SpecGroupDto group(String code, String name, SpecOptionDto... options) {
        return new SpecGroupDto(UUID.randomUUID(), code, name, List.of(options));
    }

    @Test
    void registeredSpecsComeFirstInRegistryOrderThenTheRestAsTheProductListsThem() {
        List<SpecGroupDto> groups = List.of(
                group("weight", "Weight", option("1.4", SpecUnit.KG)),
                group("processor", "Processor", option("Intel Core i7-1185G7", null)),
                group("ram", "Memory", option("16", SpecUnit.GB)),
                group("battery", "Battery", option("4000", SpecUnit.mAh)));

        List<WarrantySpecLine> specs = WarrantyProductSnapshotter.mainSpecs(groups, List.of(), null, REGISTRY);

        assertEquals(List.of(
                new WarrantySpecLine("Memory", "16 GB"),
                new WarrantySpecLine("Processor", "Intel Core i7-1185G7"),
                new WarrantySpecLine("Weight", "1.4 kg"),
                new WarrantySpecLine("Battery", "4000 mAh")), specs);
    }

    @Test
    void aVariantsCardShowsOnlyTheOptionThatWasSold() {
        SpecOptionDto sixteen = option("16", SpecUnit.GB);
        SpecOptionDto thirtyTwo = option("32", SpecUnit.GB);
        SpecGroupDto ram = new SpecGroupDto(UUID.randomUUID(), "ram", "Memory", List.of(sixteen, thirtyTwo));
        SpecGroupDto os = group("operating_system", "OS", option("Windows 11 Pro", null));
        VariantDto sold = new VariantDto(UUID.randomUUID(), "SKU-32", List.of(thirtyTwo.getId()));

        List<WarrantySpecLine> specs = WarrantyProductSnapshotter.mainSpecs(List.of(ram, os), List.of(), sold, REGISTRY);

        assertEquals(new WarrantySpecLine("Memory", "32 GB"), specs.get(0));
        assertEquals(new WarrantySpecLine("OS", "Windows 11 Pro"), specs.get(1), "groups the variant did not choose from show as listed");
    }

    @Test
    void withoutAVariantEveryOptionIsListedOnce() {
        SpecGroupDto ram = new SpecGroupDto(UUID.randomUUID(), "ram", "Memory",
                List.of(option("16", SpecUnit.GB), option("32", SpecUnit.GB), option("16", SpecUnit.GB)));
        assertEquals("16 GB / 32 GB",
                WarrantyProductSnapshotter.mainSpecs(List.of(ram), List.of(), null, REGISTRY).get(0).value());
    }

    @Test
    void aCardListsAtMostSixSpecs() {
        List<SpecGroupDto> groups = List.of(
                group("a", "A", option("1", null)), group("b", "B", option("2", null)),
                group("c", "C", option("3", null)), group("d", "D", option("4", null)),
                group("e", "E", option("5", null)), group("f", "F", option("6", null)),
                group("g", "G", option("7", null)));
        assertEquals(6, WarrantyProductSnapshotter.mainSpecs(groups, List.of(), null, REGISTRY).size());
    }

    @Test
    void emptyOrNamelessGroupsAreSkipped() {
        List<SpecGroupDto> groups = List.of(
                group("ram", "Memory"),
                group("storage", " ", option("512", SpecUnit.GB)),
                group("processor", "Processor", option("  ", null)));
        assertEquals(List.of(), WarrantyProductSnapshotter.mainSpecs(groups, List.of(), null, REGISTRY));
    }

    @Test
    void colourComesFromTheVariantOrFromAProductWithOnlyOne() {
        ColorOptionDto blue = new ColorOptionDto(UUID.randomUUID(), "Sierra Blue", "#9fb4c7", List.of());
        ColorOptionDto graphite = new ColorOptionDto(UUID.randomUUID(), "Graphite", "#41424c", List.of());
        VariantDto soldBlue = new VariantDto(UUID.randomUUID(), "SKU-B", List.of(blue.getId()));

        assertEquals(List.of(new WarrantySpecLine("Colour", "Sierra Blue")),
                WarrantyProductSnapshotter.mainSpecs(List.of(), List.of(blue, graphite), soldBlue, REGISTRY));
        assertEquals(List.of(new WarrantySpecLine("Colour", "Graphite")),
                WarrantyProductSnapshotter.mainSpecs(List.of(), List.of(graphite), null, REGISTRY));
        assertEquals(List.of(), WarrantyProductSnapshotter.mainSpecs(List.of(), List.of(blue, graphite), null, REGISTRY),
                "order items do not record a swatch, so an unknown colour is left off rather than guessed");
    }

    @Test
    void valuesReadNaturallyWithTheirUnits() {
        assertEquals("16 GB", WarrantyProductSnapshotter.formatValue("16", SpecUnit.GB));
        assertEquals("14\"", WarrantyProductSnapshotter.formatValue("14", SpecUnit.INCH));
        assertEquals("120 Hz", WarrantyProductSnapshotter.formatValue("120", SpecUnit.HZ));
        assertEquals("2.4 GHz", WarrantyProductSnapshotter.formatValue("2.4", SpecUnit.GHz));
        assertEquals("16GB", WarrantyProductSnapshotter.formatValue("16GB", SpecUnit.GB), "unit not doubled");
        assertEquals("14 inch", WarrantyProductSnapshotter.formatValue("14 inch", SpecUnit.INCH));
        assertEquals("Windows 11", WarrantyProductSnapshotter.formatValue(" Windows 11 ", null));
        assertNull(WarrantyProductSnapshotter.formatValue(" ", SpecUnit.GB));
    }

    @Test
    void conditionNamesTheGradeOfARefurbishedUnit() {
        assertEquals("New", WarrantyProductSnapshotter.condition(false, null));
        assertEquals("New", WarrantyProductSnapshotter.condition(null, "A"));
        assertEquals("Refurbished · Grade A", WarrantyProductSnapshotter.condition(true, "A"));
        assertEquals("Refurbished", WarrantyProductSnapshotter.condition(true, null));
    }
}
