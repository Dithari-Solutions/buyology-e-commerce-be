package com.buyology.ecommerce.productimport.service;

import com.buyology.ecommerce.product.domain.Brand;
import com.buyology.ecommerce.product.domain.BrandTranslation;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.dto.CreateProductRequest;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.dto.ProductTranslationRequest;
import com.buyology.ecommerce.product.repository.BrandRepository;
import com.buyology.ecommerce.product.repository.BrandTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.service.ProductService;
import com.buyology.ecommerce.productimport.domain.ProductImportJob;
import com.buyology.ecommerce.productimport.domain.ProductImportRow;
import com.buyology.ecommerce.productimport.repository.ProductImportJobRepository;
import com.buyology.ecommerce.productimport.repository.ProductImportRowRepository;
import com.buyology.ecommerce.productimport.service.SpreadsheetReader.RawRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk product import: spreadsheet in, draft catalogue products out.
 *
 * <p>The shape is deliberately upload → extract → <strong>review</strong> → import. Nothing
 * reaches the catalogue until a person has approved what the sheet was understood to say, because
 * the failure being guarded against is not a crash — it is forty-five confidently wrong products,
 * each of which looks fine until a customer receives the wrong machine.
 *
 * <p>Everything created here is {@code DRAFT} and {@code needsFulfilment = true}. Three separate
 * things then keep it off the storefront until a human finishes it, which is what makes importing
 * a half-known product safe:
 * <ol>
 *   <li>{@code DRAFT} is excluded from the public product specification and from search indexing;
 *   <li>no {@code StoreProduct} row is created, so the product has no price — and
 *       {@code CartService.addItem} refuses any product without an active store listing;
 *   <li>{@code needsFulfilment} puts it in the dashboard's fulfilment queue with the notes
 *       explaining what still has to be decided.
 * </ol>
 *
 * <p>Images are explicitly not handled. They are a manual task, by request, and pretending
 * otherwise would mean inventing stock photography for specific refurbished units.
 */
@Service
public class ProductImportService {

    private static final Logger log = LoggerFactory.getLogger(ProductImportService.class);

    /** Status for products that exist but must not be sold yet. */
    public static final String DRAFT_STATUS = "DRAFT";

    private final SpreadsheetReader reader;
    private final ProductImportAiService ai;
    private final ProductImportJobRepository jobRepo;
    private final ProductImportRowRepository rowRepo;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final BrandTranslationRepository brandTranslationRepository;
    /** One transaction per imported row, so a bad row never rolls back the good ones. */
    private final TransactionTemplate tx;

    public ProductImportService(SpreadsheetReader reader,
                                ProductImportAiService ai,
                                ProductImportJobRepository jobRepo,
                                ProductImportRowRepository rowRepo,
                                ProductService productService,
                                ProductRepository productRepository,
                                ProductCategoryRepository categoryRepository,
                                BrandRepository brandRepository,
                                BrandTranslationRepository brandTranslationRepository,
                                PlatformTransactionManager transactionManager) {
        this.reader = reader;
        this.ai = ai;
        this.jobRepo = jobRepo;
        this.rowRepo = rowRepo;
        this.productService = productService;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.brandTranslationRepository = brandTranslationRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ─── Upload ───────────────────────────────────────────────────────────────

    /**
     * Parses the uploaded sheet and stores its rows verbatim. Extraction is kicked off separately
     * by the caller, once this transaction has committed.
     *
     * @param adminId   captured on the request thread — the async worker has no SecurityContext
     */
    @Transactional
    public ProductImportJob createJob(MultipartFile file,
                                      UUID categoryId,
                                      UUID brandId,
                                      UUID adminId,
                                      String adminName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException(
                    "Choose the category these products belong to. The sheet does not say, and a "
                            + "category guessed from a product title is very hard to unpick later.");
        }
        if (categoryRepository.findById(categoryId).isEmpty()) {
            throw new IllegalArgumentException("Category not found: " + categoryId);
        }
        if (!ai.isAvailable()) {
            throw new IllegalStateException(
                    "Product import needs the Anthropic API key to be configured on the server.");
        }

        SpreadsheetReader.Parsed parsed;
        try (var in = file.getInputStream()) {
            parsed = reader.read(in, file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file.");
        }

        ProductImportJob job = new ProductImportJob();
        job.setFileName(file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename());
        job.setStatus(ProductImportJob.Status.UPLOADED);
        job.setTotalRows(parsed.rows().size());
        job.setDefaultCategoryId(categoryId);
        job.setDefaultBrandId(brandId);
        job.setCreatedByAdminId(adminId);
        job.setCreatedByAdminName(adminName);
        job = jobRepo.save(job);

        List<ProductImportRow> rows = new ArrayList<>();
        for (RawRow raw : parsed.rows()) {
            ProductImportRow row = new ProductImportRow();
            row.setJobId(job.getId());
            row.setRowNumber(raw.rowNumber());
            row.setRawCode(raw.code());
            row.setRawText(raw.text());
            row.setRawQuantity(raw.quantity());
            row.setStatus(ProductImportRow.Status.PENDING);
            rows.add(row);
        }
        rowRepo.saveAll(rows);

        log.info("[IMPORT] Job {} created from {} with {} row(s) by {}",
                job.getId(), job.getFileName(), rows.size(), adminName);
        return job;
    }

    // ─── Extraction ───────────────────────────────────────────────────────────

    /**
     * Runs Claude over every pending row.
     *
     * <p>Async because a sheet of fifty rows is ten API calls with thinking enabled, which is far
     * longer than an HTTP request should be held open. The dashboard polls the job.
     */
    @Async
    public void runExtraction(UUID jobId) {
        ProductImportJob job = jobRepo.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("[IMPORT] Extraction asked for unknown job {}", jobId);
            return;
        }
        try {
            markStatus(job, ProductImportJob.Status.EXTRACTING);

            List<ProductImportRow> rows = rowRepo.findByJobIdOrderByRowNumberAsc(jobId);
            List<RawRow> raw = rows.stream()
                    .map(r -> new RawRow(r.getRowNumber(), r.getRawCode(), r.getRawText(), r.getRawQuantity()))
                    .toList();

            Map<Integer, SpecExtraction> extracted = ai.extractAll(raw);

            int ok = 0, failed = 0;
            for (ProductImportRow row : rows) {
                SpecExtraction e = extracted.get(row.getRowNumber());
                if (e == null) {
                    row.setStatus(ProductImportRow.Status.EXTRACTION_FAILED);
                    row.setErrorMessage("Could not be read automatically — enter this one by hand.");
                    failed++;
                } else {
                    apply(row, e);
                    row.setStatus(ProductImportRow.Status.EXTRACTED);
                    ok++;
                }
            }
            rowRepo.saveAll(rows);

            job.setExtractedRows(ok);
            job.setFailedRows(failed);
            markStatus(job, ProductImportJob.Status.READY_FOR_REVIEW);
            log.info("[IMPORT] Job {} extracted: {} ok, {} failed", jobId, ok, failed);

        } catch (RuntimeException e) {
            log.error("[IMPORT] Job {} failed during extraction", jobId, e);
            job.setErrorMessage(truncate(e.getMessage(), 1000));
            markStatus(job, ProductImportJob.Status.FAILED);
        }
    }

    private static void apply(ProductImportRow row, SpecExtraction e) {
        row.setBrand(trim(e.brand(), 120));
        row.setModel(trim(e.model(), 200));
        row.setDeviceType(trim(e.deviceType(), 40));
        row.setProcessor(trim(e.processor(), 120));
        row.setProcessorGen(e.processorGeneration());
        row.setRamGb(e.ramGb());
        row.setStorageGb(e.storageGb());
        row.setStorageType(trim(e.storageType(), 20));
        row.setScreenInches(e.screenInches() == null ? null : BigDecimal.valueOf(e.screenInches()));
        row.setOperatingSystem(trim(e.operatingSystem(), 80));
        row.setGpuGb(e.gpuGb());
        row.setTouchscreen(e.touchscreen());
        row.setColour(trim(e.colour(), 60));
        row.setTitleEn(trim(e.titleEn(), 255));
        row.setTitleAz(trim(e.titleAz(), 255));
        row.setTitleAr(trim(e.titleAr(), 255));
        row.setDescriptionEn(e.descriptionEn());
        row.setDescriptionAz(e.descriptionAz());
        row.setDescriptionAr(e.descriptionAr());
        row.setConfidence(trim(e.confidence(), 20));
        row.setNeedsReview(ProductImportAiService.joinNeedsReview(e));
        row.setErrorMessage(null);
    }

    // ─── Import ───────────────────────────────────────────────────────────────

    /**
     * Creates a draft product for each approved row.
     *
     * <p>Each row is committed on its own, so one bad row never rolls back the rest — the same
     * choice {@code ErpProductImportService} makes, and for the same reason: a half-imported sheet
     * that names its failures is far more useful than an all-or-nothing refusal.
     */
    public ProductImportJob importRows(UUID jobId, List<UUID> rowIds) {
        ProductImportJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found: " + jobId));

        List<ProductImportRow> all = rowRepo.findByJobIdOrderByRowNumberAsc(jobId);
        List<ProductImportRow> selected = all.stream()
                .filter(r -> rowIds == null || rowIds.isEmpty() || rowIds.contains(r.getId()))
                .filter(r -> r.getStatus() == ProductImportRow.Status.EXTRACTED)
                .toList();

        if (selected.isEmpty()) {
            throw new IllegalArgumentException("None of the selected rows are ready to import.");
        }

        markStatus(job, ProductImportJob.Status.IMPORTING);

        int imported = 0;
        for (ProductImportRow row : selected) {
            try {
                UUID productId = tx.execute(status -> createDraftProduct(job, row));
                row.setProductId(productId);
                row.setStatus(ProductImportRow.Status.IMPORTED);
                row.setErrorMessage(null);
                imported++;
            } catch (RuntimeException e) {
                log.error("[IMPORT] Job {} row {} could not be imported: {}",
                        jobId, row.getRowNumber(), e.toString());
                row.setStatus(ProductImportRow.Status.IMPORT_FAILED);
                row.setErrorMessage(truncate(e.getMessage(), 1000));
            }
            rowRepo.save(row);
        }

        job.setImportedRows(job.getImportedRows() + imported);
        job.setCompletedAt(java.time.Instant.now());
        markStatus(job, ProductImportJob.Status.COMPLETED);
        log.info("[IMPORT] Job {} imported {} of {} selected row(s)", jobId, imported, selected.size());
        return job;
    }

    /**
     * Builds one draft product from an extracted row.
     *
     * <p>Goes through {@link ProductService#createProduct} rather than writing entities directly,
     * so SKU generation, translation slugs and search indexing all behave exactly as they do for a
     * product created by hand in the dashboard. There is no second code path to keep in step.
     */
    private UUID createDraftProduct(ProductImportJob job, ProductImportRow row) {
        CreateProductRequest req = new CreateProductRequest();
        req.setCategoryId(job.getDefaultCategoryId());
        req.setProductType(Product.ProductType.SIMPLE);
        req.setStatus(DRAFT_STATUS);
        req.setIsSuperDeal(false);
        req.setIsLimitedStock(false);

        // The supplier's own code is the most useful SKU there is — it is what the warehouse and
        // the supplier both say. Used only when it is free; a repeat (the same sheet uploaded
        // twice, or a code reused across suppliers) falls back to a generated one rather than
        // failing the row on a unique-constraint violation.
        String code = row.getRawCode();
        if (code != null && !code.isBlank() && !productRepository.existsBySku(code.trim())) {
            req.setSku(code.trim());
        }

        // What the sheet says is on hand. Absent means one unit, not zero — a zero would import a
        // product nobody can ever buy, and these sheets routinely leave the column blank for
        // single items.
        int qty = row.getRawQuantity() == null ? 1 : Math.max(0, row.getRawQuantity());
        req.setStockQuantity(qty);
        req.setAvailabilityStatus(qty > 0
                ? Product.AvailabilityStatus.IN_STOCK
                : Product.AvailabilityStatus.OUT_OF_STOCK);

        req.setBrandId(resolveBrandId(job, row));
        req.setTranslations(buildTranslations(row));

        var response = productService.createProduct(req, null);
        ProductResponse created = response.getBody() == null ? null : response.getBody().getData();
        if (created == null || created.getId() == null) {
            throw new IllegalStateException("Product creation returned no product.");
        }

        // Stamped after creation because CreateProductRequest has no field for them — these are
        // import bookkeeping, not product attributes an admin would ever type.
        Product product = productRepository.findById(created.getId()).orElseThrow();
        product.setNeedsFulfilment(true);
        product.setImportJobId(job.getId());
        product.setImportNotes(buildNotes(row));
        productRepository.save(product);

        return product.getId();
    }

    /**
     * Fills all six translation fields, falling back rather than failing.
     *
     * <p>Every field on {@code ProductTranslationRequest} is {@code @NotBlank} and the columns are
     * not nullable, so a row whose Arabic title came back empty cannot simply be passed through.
     * Falling back to English keeps the product importable and, crucially, visible in the
     * fulfilment queue where a person will fix it — whereas failing the row would leave the
     * machine out of the catalogue entirely, which is the worse of the two.
     *
     * <p>Every fallback used is recorded in {@link #buildNotes}, so "this description is in the
     * wrong language" is something the queue tells you rather than something a customer does.
     */
    private static ProductTranslationRequest buildTranslations(ProductImportRow row) {
        String titleEn = firstNonBlank(row.getTitleEn(), row.getRawText());
        String descEn = firstNonBlank(row.getDescriptionEn(), titleEn);

        ProductTranslationRequest t = new ProductTranslationRequest();
        t.setTitleEn(cap(titleEn, 255));
        t.setTitleAz(cap(firstNonBlank(row.getTitleAz(), titleEn), 255));
        t.setTitleAr(cap(firstNonBlank(row.getTitleAr(), titleEn), 255));
        t.setDescriptionEn(descEn);
        t.setDescriptionAz(firstNonBlank(row.getDescriptionAz(), descEn));
        t.setDescriptionAr(firstNonBlank(row.getDescriptionAr(), descEn));
        return t;
    }

    /**
     * Resolves the brand the extraction named, creating it if the shop does not have it yet.
     *
     * <p>Matches {@code ErpProductImportService.resolveBrand} so the two importers cannot end up
     * creating rival "Lenovo" brands.
     */
    private UUID resolveBrandId(ProductImportJob job, ProductImportRow row) {
        String name = row.getBrand();
        if (name == null || name.isBlank()) {
            return job.getDefaultBrandId();
        }
        String clean = name.trim();
        return brandTranslationRepository
                .findFirstByLanguageIgnoreCaseAndNameIgnoreCase("EN", clean)
                .map(BrandTranslation::getBrand)
                .map(Brand::getId)
                .orElseGet(() -> createBrand(clean));
    }

    private UUID createBrand(String name) {
        Brand brand = new Brand();
        brand.setStatus("ACTIVE");
        Brand saved = brandRepository.save(brand);
        for (String lang : List.of("EN", "AZ", "AR")) {
            brandTranslationRepository.save(new BrandTranslation(saved, lang, name));
        }
        log.info("[IMPORT] Created brand '{}'", name);
        return saved.getId();
    }

    /**
     * The note a human reads in the fulfilment queue.
     *
     * <p>Carries the original spreadsheet line, so finishing a product never means going back to
     * the supplier's file to work out what the importer meant.
     */
    private static String buildNotes(ProductImportRow row) {
        StringBuilder sb = new StringBuilder();
        sb.append("Imported from spreadsheet row ").append(row.getRowNumber()).append(".\n");
        sb.append("Original text: ").append(row.getRawText()).append('\n');
        if (row.getConfidence() != null) {
            sb.append("Extraction confidence: ").append(row.getConfidence()).append('\n');
        }
        if (row.getNeedsReview() != null && !row.getNeedsReview().isBlank()) {
            sb.append("\nNeeds a decision:\n").append(row.getNeedsReview()).append('\n');
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(row.getTitleAz())) missing.add("Azerbaijani title (English used instead)");
        if (isBlank(row.getTitleAr())) missing.add("Arabic title (English used instead)");
        if (isBlank(row.getDescriptionAz())) missing.add("Azerbaijani description (English used instead)");
        if (isBlank(row.getDescriptionAr())) missing.add("Arabic description (English used instead)");
        if (!missing.isEmpty()) {
            sb.append("\nStill to translate:\n- ").append(String.join("\n- ", missing)).append('\n');
        }
        sb.append("\nImages have not been added — that is a manual task.");
        return sb.toString();
    }

    // ─── Reads ────────────────────────────────────────────────────────────────

    public List<ProductImportJob> listJobs() {
        return jobRepo.findTop20ByOrderByCreatedAtDesc();
    }

    public ProductImportJob getJob(UUID jobId) {
        return jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found: " + jobId));
    }

    public List<ProductImportRow> getRows(UUID jobId) {
        return rowRepo.findByJobIdOrderByRowNumberAsc(jobId);
    }

    /** The fulfilment queue: imported products still waiting on a person. */
    public List<Product> getFulfilmentQueue() {
        return productRepository.findByNeedsFulfilmentTrueAndStatusNot("DELETED");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void markStatus(ProductImportJob job, ProductImportJob.Status status) {
        job.setStatus(status);
        jobRepo.save(job);
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String cap(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String firstNonBlank(String a, String b) {
        return isBlank(a) ? b : a;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
