-- Bulk product import from a spreadsheet, and the stock guard that imported products depend on.
--
-- These ship together because the import is what makes the gap dangerous. Today stock is only
-- enforced on StoreProductVariant rows: OrderService.createOrder calls decrementStock, whose
-- "and v.stock >= :qty" is a genuine atomic guard. A cart line with no variant skips that block
-- entirely and only soft-decrements products.stock_quantity through Math.max(0, ...), which by
-- construction can never refuse an order. Imported products are SIMPLE, variant-less, and carry a
-- real unit count from the supplier's sheet — so without the guard below, importing a sheet that
-- says "1 unit" creates a listing that can be bought any number of times.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh database the
-- products table does not exist yet and Hibernate creates it from the entity instead. Mirrors
-- V8 / V11 / V17 / V18 / V21 / V22 / V26 / V49 / V50.

-- ---------------------------------------------------------------------------
-- 1. The "not fully filled" marker on products
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.products') IS NOT NULL THEN

        -- Set by the importer, cleared by a human. Kept separate from status because it answers a
        -- different question: status is "may this be sold", needs_fulfilment is "has a person
        -- checked it and added the photos". A product can be corrected and published while other
        -- imported rows are still waiting, and the dashboard filters on this to show the queue.
        ALTER TABLE products ADD COLUMN IF NOT EXISTS needs_fulfilment BOOLEAN NOT NULL DEFAULT FALSE;

        -- What the extraction was unsure about, one short phrase per line, plus the original
        -- spreadsheet text. This is the whole reason a human can finish the job quickly: without
        -- it they are re-reading the supplier's sheet to work out what the importer meant.
        ALTER TABLE products ADD COLUMN IF NOT EXISTS import_notes TEXT;

        -- Provenance. Answers "where did this product come from" a year from now, and lets an
        -- import be reviewed, or reverted, as a unit.
        ALTER TABLE products ADD COLUMN IF NOT EXISTS import_job_id UUID;

        -- Partial index: the fulfilment queue is a handful of rows against a full catalogue, and
        -- this is the only query that reads the column.
        CREATE INDEX IF NOT EXISTS idx_products_needs_fulfilment
            ON products (needs_fulfilment)
            WHERE needs_fulfilment = TRUE;

        CREATE INDEX IF NOT EXISTS idx_products_import_job
            ON products (import_job_id)
            WHERE import_job_id IS NOT NULL;

        -- Backfill safety for the stock guard. The guard refuses a sale when stock_quantity is 0,
        -- and treats NULL as "not tracked" exactly as the column comment in Product.java already
        -- says. Nothing is backfilled here on purpose: turning today's NULLs into 0 would make
        -- every untracked product unbuyable the moment this deploys.
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2. Import jobs
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_import_jobs (
    id                    UUID PRIMARY KEY,
    file_name             VARCHAR(400)  NOT NULL,
    -- UPLOADED -> EXTRACTING -> READY_FOR_REVIEW -> IMPORTING -> COMPLETED | FAILED.
    -- Two human gates, deliberately. Extraction is cheap to redo and expensive to get wrong, so
    -- nothing is written to the catalogue until someone has looked at what was parsed.
    status                VARCHAR(30)   NOT NULL DEFAULT 'UPLOADED',
    total_rows            INTEGER       NOT NULL DEFAULT 0,
    extracted_rows        INTEGER       NOT NULL DEFAULT 0,
    failed_rows           INTEGER       NOT NULL DEFAULT 0,
    imported_rows         INTEGER       NOT NULL DEFAULT 0,
    -- The category and store every row in this import lands in. The sheet does not carry them and
    -- guessing a category from a product title is exactly the kind of silent mistake that is
    -- unpickable later, so the admin chooses once at upload time.
    default_category_id   UUID,
    default_brand_id      UUID,
    error_message         VARCHAR(1000),
    -- Captured on the request thread; see the note in V50. Extraction runs @Async, where the
    -- SecurityContext is already gone.
    created_by_admin_id   UUID          NOT NULL,
    created_by_admin_name VARCHAR(200),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_product_import_jobs_created
    ON product_import_jobs (created_at DESC);

-- ---------------------------------------------------------------------------
-- 3. Import rows — one per spreadsheet line, carrying both the mess and the parse
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_import_rows (
    id                 UUID PRIMARY KEY,
    job_id             UUID          NOT NULL REFERENCES product_import_jobs (id) ON DELETE CASCADE,
    row_number         INTEGER       NOT NULL,

    -- Verbatim from the sheet, never normalised. When an extraction turns out to be wrong, this is
    -- the only record of what the supplier actually wrote.
    raw_code           VARCHAR(200),
    raw_text           TEXT          NOT NULL,
    raw_quantity       INTEGER,

    -- PENDING -> EXTRACTED | EXTRACTION_FAILED -> IMPORTED | IMPORT_FAILED | SKIPPED
    status             VARCHAR(30)   NOT NULL DEFAULT 'PENDING',

    -- The extraction, stored as columns rather than a JSON blob so the review screen can sort and
    -- filter on it, and so a bad extraction is visible in a query instead of needing a parser.
    brand              VARCHAR(120),
    model              VARCHAR(200),
    device_type        VARCHAR(40),
    processor          VARCHAR(120),
    processor_gen      INTEGER,
    ram_gb             INTEGER,
    storage_gb         INTEGER,
    storage_type       VARCHAR(20),
    screen_inches      NUMERIC(5, 2),
    operating_system   VARCHAR(80),
    gpu_gb             INTEGER,
    touchscreen        BOOLEAN,
    colour             VARCHAR(60),

    title_en           VARCHAR(255),
    title_az           VARCHAR(255),
    title_ar           VARCHAR(255),
    description_en     TEXT,
    description_az     TEXT,
    description_ar     TEXT,

    -- HIGH | MEDIUM | LOW, and the model's own list of what a human should settle. A LOW row is
    -- not an error — it is a row the sheet did not describe well enough, and it is surfaced for
    -- review rather than guessed at.
    confidence         VARCHAR(20),
    needs_review       TEXT,

    -- Set once the row becomes a catalogue product; null while it is still just a parse.
    product_id         UUID,
    error_message      VARCHAR(1000),

    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_product_import_rows_job_row UNIQUE (job_id, row_number)
);

CREATE INDEX IF NOT EXISTS idx_product_import_rows_job
    ON product_import_rows (job_id, row_number);
