package com.buyology.ecommerce.store;

import com.buyology.ecommerce.product.domain.Brand;
import com.buyology.ecommerce.product.domain.BrandTranslation;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.domain.ProductCategoryTranslation;
import com.buyology.ecommerce.product.domain.ProductMedia;
import com.buyology.ecommerce.product.domain.ProductTranslation;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.store.domain.Country;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.domain.StoreProductVariant;
import com.buyology.ecommerce.store.enums.StoreProductExportFormat;
import com.buyology.ecommerce.store.service.StoreProductExportService;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The store product export, end to end against Postgres: the fetch queries, the lazy associations
 * they read inside one transaction, and the values that land in the file.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.web-base-url=https://shop.example",
        "app.api-base-url=https://api.example/"
})
@Import(StoreProductExportService.class)
@Testcontainers
class StoreProductExportIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    StoreProductExportService exportService;

    @Autowired
    EntityManager em;

    @Test
    void everyLiveListingIsExportedWithItsResolvedDetails() throws Exception {
        Country country = new Country("Z91", "Testland", "AED");
        em.persist(country);
        Store store = new Store();
        store.setCountry(country);
        store.setName("Dubai Mall");
        store.setSlug("dubai-mall-" + UUID.randomUUID());
        em.persist(store);

        ProductCategory category = new ProductCategory();
        category.setStatus("ACTIVE");
        em.persist(category);
        em.persist(new ProductCategoryTranslation(category, "EN", "Smartphones", null, "smartphones-" + UUID.randomUUID()));
        Brand brand = new Brand();
        em.persist(brand);
        em.persist(new BrandTranslation(brand, "EN", "Apple"));

        Product phone = product(category, "IP15P");
        phone.setBrand(brand);
        phone.setAvailableQuantity(3);
        em.persist(phone);
        em.persist(new ProductTranslation(phone, "AZ", "iPhone AZ", "AZ desc", "iphone-az"));
        em.persist(new ProductTranslation(phone, "EN", "iPhone 15 Pro", "Titanium.", "iphone-15-pro"));
        ProductMedia gallery = new ProductMedia(phone, ProductMedia.MediaType.IMAGE, "products/a.webp", null, false, 0);
        ProductMedia cover = new ProductMedia(phone, ProductMedia.MediaType.IMAGE, "products/b.webp", null, true, 1);
        em.persist(gallery);
        em.persist(cover);
        ProductVariant variant = new ProductVariant(phone, "IP15P-BLK-" + UUID.randomUUID());
        em.persist(variant);

        StoreProduct phoneListing = new StoreProduct(store, phone, new BigDecimal("4000.00"));
        phoneListing.setDiscountType(Product.DiscountType.PERCENTAGE);
        phoneListing.setDiscountValue(new BigDecimal("10"));
        em.persist(phoneListing);
        StoreProductVariant listedVariant = new StoreProductVariant(phoneListing, variant, new BigDecimal("4000.00"));
        listedVariant.setStock(8);
        em.persist(listedVariant);

        Product cable = product(category, "CABLE");
        em.persist(cable);
        em.persist(new ProductTranslation(cable, "EN", "A cable", "", "a-cable"));
        StoreProduct cableListing = new StoreProduct(store, cable, new BigDecimal("50.00"));
        cableListing.setIsActive(false);
        cableListing.setDiscountType(Product.DiscountType.FIXED);
        cableListing.setDiscountValue(new BigDecimal("40.00"));
        cableListing.setDiscountEndsAt(Instant.now().minusSeconds(86_400));
        em.persist(cableListing);

        Product removed = product(category, "GONE");
        em.persist(removed);
        StoreProduct removedListing = new StoreProduct(store, removed, new BigDecimal("1.00"));
        removedListing.setDeletedAt(Instant.now());
        em.persist(removedListing);

        em.flush();
        em.clear();

        StoreProductExportService.ExportFile file = exportService.export(store.getId(), StoreProductExportFormat.XLSX);
        assertTrue(file.fileName().startsWith("dubai-mall-"));
        assertTrue(file.fileName().endsWith(".xlsx"));

        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(file.content()));
        Sheet products = wb.getSheet("Products");
        assertEquals(2, products.getLastRowNum(), "the removed listing is not exported");

        Row cableRow = products.getRow(1);   // sorted by name: "A cable" first
        assertEquals("A cable", cableRow.getCell(1).getStringCellValue());
        assertEquals("Inactive", cableRow.getCell(13).getStringCellValue());
        assertEquals("Not tracked", cableRow.getCell(11).getStringCellValue());
        assertEquals(CellType.BLANK, cableRow.getCell(7).getCellType(), "an ended sale has no sale price");
        assertTrue(cableRow.getCell(8).getStringCellValue().startsWith("Fixed sale price (ended "));

        Row phoneRow = products.getRow(2);
        assertEquals("iPhone 15 Pro", phoneRow.getCell(1).getStringCellValue(), "the English title wins");
        assertEquals("Titanium.", phoneRow.getCell(5).getStringCellValue());
        assertEquals("Apple", phoneRow.getCell(3).getStringCellValue());
        assertEquals("Smartphones", phoneRow.getCell(4).getStringCellValue());
        assertEquals(4000.0, phoneRow.getCell(6).getNumericCellValue());
        assertEquals(3600.0, phoneRow.getCell(7).getNumericCellValue());
        assertEquals("AED", phoneRow.getCell(9).getStringCellValue());
        assertEquals(3, (int) phoneRow.getCell(11).getNumericCellValue(), "8 in the store, but only 3 may be sold");
        assertEquals("https://shop.example/en/shop/iphone-15-pro", phoneRow.getCell(15).getStringCellValue());
        assertEquals("https://api.example/api/product/media/" + cover.getId(), phoneRow.getCell(16).getStringCellValue());
        assertEquals("https://api.example/api/product/media/" + gallery.getId(), phoneRow.getCell(17).getStringCellValue());

        assertEquals(variant.getSku(), wb.getSheet("Variants").getRow(1).getCell(2).getStringCellValue());

        StoreProductExportService.ExportFile pdf = exportService.export(store.getId(), StoreProductExportFormat.PDF);
        assertEquals("application/pdf", pdf.contentType());
        assertTrue(pdf.content().length > 0);
    }

    private static Product product(ProductCategory category, String skuPrefix) {
        return new Product(category, null, Product.ProductType.SIMPLE, false, null,
                skuPrefix + "-" + UUID.randomUUID(), "ACTIVE", Product.AvailabilityStatus.IN_STOCK, false, false);
    }
}
