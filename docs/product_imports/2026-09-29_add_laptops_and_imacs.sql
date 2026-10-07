-- =====================================================================================================
-- Buyology catalogue: add 24 products from the 2026-09-29 supplier sheet as DRAFTS
--   21 laptops  -> category "Laptop"
--   3 iMacs    -> category "Desktop" (created here if it does not exist)
--
-- Nothing here goes live. Every product is created exactly the way the dashboard's bulk importer
-- creates one (ProductImportService): status DRAFT, needs_fulfilment = true, no store listing, no price,
-- no images. DRAFT keeps it out of the storefront and search; needs_fulfilment puts it in the dashboard's
-- fulfilment queue, and import_notes on each product carries the sheet line, the sheet price and
-- anything that needs checking before it is published.
--
-- SKU = the sheet's DTA code. The script never overwrites anything. A product is SKIPPED, and reported,
-- when its SKU already exists (any status, including drafts and deleted products), or when its title or
-- URL slug is already used by another product in the same language - the same rule the dashboard enforces.
-- Re-running the file is safe: everything added the first time is then reported as already existing.
--
-- Left out on purpose (not in this file):
--   already live on the storefront: DTA1101, DTAX0648, DTAX1262, DTAX1245, DTAX1408
--   tablets, held back:             DTA1167, DTAX1558, DTAX1559, DTAX0325, DTAX1274  (DTAX1274 is also already live)
--
-- HOW TO RUN (PostgreSQL 15, against the production database):
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f 2026-09-29_add_laptops_and_imacs.sql
--   or open it in DBeaver / pgAdmin and execute it as a script.
-- It runs in one transaction: any error rolls back everything, so it is all or nothing.
-- DRY RUN: change the COMMIT near the bottom to ROLLBACK. Nothing is saved, and the messages
-- (NOTICE) output still lists every SKU as ADDED or SKIPPED with the reason.
--
-- AFTER RUNNING: the report at the end lists every SKU as ADDED or SKIPPED, with the reason.
-- Products appear in the dashboard under the fulfilment queue. Add images, add each one to a store
-- with a price, then publish. Editing a product (e.g. adding images) updates the search index;
-- if a published product does not show in search, run the dashboard's reindex
-- (POST /api/admin/product/reindex).
--
-- UNDO (only while they are still drafts):
--   DELETE FROM product_spec_options  WHERE group_id IN (SELECT g.id FROM product_spec_groups g JOIN products p ON p.id = g.product_id WHERE p.import_notes LIKE 'Added by SQL batch 2026-09-29.%' AND p.status = 'DRAFT');
--   DELETE FROM product_spec_groups   WHERE product_id IN (SELECT id FROM products WHERE import_notes LIKE 'Added by SQL batch 2026-09-29.%' AND status = 'DRAFT');
--   DELETE FROM product_translations  WHERE product_id IN (SELECT id FROM products WHERE import_notes LIKE 'Added by SQL batch 2026-09-29.%' AND status = 'DRAFT');
--   DELETE FROM products              WHERE import_notes LIKE 'Added by SQL batch 2026-09-29.%' AND status = 'DRAFT';
-- =====================================================================================================

-- The report. Session temp tables, created before the transaction so they outlive COMMIT or ROLLBACK.
CREATE TEMP TABLE IF NOT EXISTS bl_report (
    seq     serial,
    sku     text,
    outcome text,
    detail  text,
    title   text
);
TRUNCATE bl_report;

CREATE TEMP TABLE IF NOT EXISTS bl_category (key text PRIMARY KEY, id uuid NOT NULL);
TRUNCATE bl_category;

BEGIN;


-- -----------------------------------------------------------------------------------------------------
-- 0. Safety check. These tables were created by Hibernate, so no column has a database default. If
--    production has a required column this script does not know about, stop now with its name instead
--    of failing halfway through with a constraint error.
-- -----------------------------------------------------------------------------------------------------
DO $$
DECLARE
    v_missing text;
BEGIN
    SELECT string_agg(c.table_name || '.' || c.column_name, ', ' ORDER BY c.table_name, c.column_name)
      INTO v_missing
      FROM information_schema.columns c
     WHERE c.table_schema = 'public'
       AND c.is_nullable = 'NO'
       AND c.column_default IS NULL
       AND c.is_identity = 'NO'
       AND (c.table_name, c.column_name) NOT IN (
            ('products', 'id'), ('products', 'category_id'), ('products', 'product_type'),
            ('products', 'is_refurbished'), ('products', 'refurb_grade'), ('products', 'sku'),
            ('products', 'availability_status'), ('products', 'is_super_deal'), ('products', 'is_limited_stock'),
            ('products', 'stock_quantity'), ('products', 'available_quantity'), ('products', 'status'),
            ('products', 'is_active'), ('products', 'needs_fulfilment'), ('products', 'import_notes'),
            ('products', 'brand_id'), ('products', 'created_at'), ('products', 'updated_at'),
            ('product_translations', 'id'), ('product_translations', 'product_id'),
            ('product_translations', 'language'), ('product_translations', 'title'),
            ('product_translations', 'description'), ('product_translations', 'slug'),
            ('product_translations', 'created_at'), ('product_translations', 'updated_at'),
            ('product_spec_groups', 'id'), ('product_spec_groups', 'product_id'),
            ('product_spec_groups', 'global_spec_group_id'), ('product_spec_groups', 'code'),
            ('product_spec_groups', 'created_at'), ('product_spec_groups', 'updated_at'),
            ('product_spec_options', 'id'), ('product_spec_options', 'group_id'),
            ('product_spec_options', 'global_spec_option_id'), ('product_spec_options', 'value'),
            ('product_spec_options', 'unit'),
            ('global_spec_options', 'id'), ('global_spec_options', 'group_id'), ('global_spec_options', 'unit'),
            ('global_spec_options', 'display_order'), ('global_spec_options', 'created_at'),
            ('global_spec_options', 'updated_at'),
            ('global_spec_option_translations', 'id'), ('global_spec_option_translations', 'option_id'),
            ('global_spec_option_translations', 'language'), ('global_spec_option_translations', 'value'),
            ('product_categories', 'id'), ('product_categories', 'status'), ('product_categories', 'icon'),
            ('product_categories', 'created_at'), ('product_categories', 'updated_at'),
            ('product_category_translations', 'id'), ('product_category_translations', 'category_id'),
            ('product_category_translations', 'language'), ('product_category_translations', 'name'),
            ('product_category_translations', 'description'), ('product_category_translations', 'slug'),
            ('product_category_translations', 'created_at'), ('product_category_translations', 'updated_at'))
       AND c.table_name IN ('products', 'product_translations', 'product_spec_groups', 'product_spec_options',
                            'global_spec_options', 'global_spec_option_translations',
                            'product_categories', 'product_category_translations');
    IF v_missing IS NOT NULL THEN
        RAISE EXCEPTION 'Stopped before writing anything: required column(s) this script does not fill: %', v_missing;
    END IF;
END $$;


-- -----------------------------------------------------------------------------------------------------
-- 1. Helpers. pg_temp functions exist only for this database session.
-- -----------------------------------------------------------------------------------------------------

-- Attaches one spec to a product, reusing the spec library's existing option when there is one.
-- Storefront filters match product_spec_options.value, so reusing the library's exact value
-- ("16" + GB, "Intel Core i7", '14"') is what makes these products show up under the existing filters.
CREATE OR REPLACE FUNCTION pg_temp.bl_spec(p_product uuid, p_code text, p_value text, p_unit text)
RETURNS void LANGUAGE plpgsql AS $fn$
DECLARE
    v_group        uuid;
    v_option       uuid;
    v_value        text;
    v_unit         text;
    v_product_group uuid;
BEGIN
    SELECT id INTO v_group FROM global_spec_groups WHERE code = p_code;
    IF v_group IS NULL THEN
        RAISE EXCEPTION 'Spec group "%" is not in the spec library. Nothing has been written.', p_code;
    END IF;

    SELECT o.id, t.value, o.unit INTO v_option, v_value, v_unit
      FROM global_spec_options o
      JOIN global_spec_option_translations t ON t.option_id = o.id AND t.language = 'EN'
     WHERE o.group_id = v_group
       AND lower(btrim(t.value)) = lower(btrim(p_value))
       AND o.unit IS NOT DISTINCT FROM p_unit
     ORDER BY o.created_at, o.id
     LIMIT 1;

    IF v_option IS NULL THEN
        -- New value for the library (e.g. a 16" screen). Same text in all three languages: every new
        -- value in this batch is a number, a model name or an OS name.
        v_option := gen_random_uuid();
        v_value  := p_value;
        v_unit   := p_unit;
        INSERT INTO global_spec_options (id, group_id, unit, display_order, created_at, updated_at)
        VALUES (v_option, v_group, p_unit,
                COALESCE((SELECT max(display_order) + 1 FROM global_spec_options WHERE group_id = v_group), 0),
                now(), now());
        INSERT INTO global_spec_option_translations (id, option_id, language, value)
        VALUES (gen_random_uuid(), v_option, 'EN', p_value),
               (gen_random_uuid(), v_option, 'AZ', p_value),
               (gen_random_uuid(), v_option, 'AR', p_value);
        INSERT INTO bl_report (sku, outcome, detail)
        VALUES ('(spec library)', 'NEW OPTION', p_code || ' = ' || p_value || COALESCE(' ' || p_unit, ''));
        RAISE NOTICE 'New spec-library option: % = % %', p_code, p_value, COALESCE(p_unit, '');
    END IF;

    SELECT id INTO v_product_group FROM product_spec_groups WHERE product_id = p_product AND code = p_code;
    IF v_product_group IS NULL THEN
        v_product_group := gen_random_uuid();
        INSERT INTO product_spec_groups (id, product_id, global_spec_group_id, code, created_at, updated_at)
        VALUES (v_product_group, p_product, v_group, p_code, now(), now());
    END IF;

    INSERT INTO product_spec_options (id, group_id, global_spec_option_id, value, unit)
    VALUES (gen_random_uuid(), v_product_group, v_option, v_value, v_unit);
END $fn$;

-- Adds one draft product, or records why it was skipped.
CREATE OR REPLACE FUNCTION pg_temp.bl_add(
    p_sku text, p_category text, p_brand text,
    p_title_en text, p_title_az text, p_title_ar text,
    p_slug_en text, p_slug_az text, p_slug_ar text,
    p_desc_en text, p_desc_az text, p_desc_ar text,
    p_notes text, p_specs text[])
RETURNS void LANGUAGE plpgsql AS $fn$
DECLARE
    v_langs    text[] := ARRAY['EN', 'AZ', 'AR'];
    v_titles   text[] := ARRAY[p_title_en, p_title_az, p_title_ar];
    v_slugs    text[] := ARRAY[p_slug_en, p_slug_az, p_slug_ar];
    v_descs    text[] := ARRAY[p_desc_en, p_desc_az, p_desc_ar];
    v_category uuid;
    v_brand    uuid;
    v_product  uuid;
    v_clash    text;
    v_spec     text[];
BEGIN
    -- 1. SKU, across every product whatever its status, and across variant SKUs.
    SELECT format('SKU already exists: "%s", status %s', COALESCE(t.title, '(no English title)'), p.status)
      INTO v_clash
      FROM products p
      LEFT JOIN product_translations t ON t.product_id = p.id AND t.language = 'EN'
     WHERE upper(btrim(p.sku)) = upper(p_sku)
     LIMIT 1;
    IF v_clash IS NULL THEN
        SELECT format('SKU already used by a variant of product %s', v.product_id)
          INTO v_clash
          FROM product_variants v
         WHERE upper(btrim(v.sku)) = upper(p_sku)
         LIMIT 1;
    END IF;

    -- 2. Title and slug per language, ignoring DELETED products: ProductService.saveTranslations' rule.
    FOR i IN 1..3 LOOP
        EXIT WHEN v_clash IS NOT NULL;
        SELECT format('%s title "%s" is already used by %s', v_langs[i], v_titles[i], p.sku)
          INTO v_clash
          FROM product_translations t
          JOIN products p ON p.id = t.product_id
         WHERE t.language = v_langs[i] AND lower(t.title) = lower(v_titles[i]) AND p.status <> 'DELETED'
         LIMIT 1;
        EXIT WHEN v_clash IS NOT NULL;
        SELECT format('%s URL slug "%s" is already used by %s', v_langs[i], v_slugs[i], p.sku)
          INTO v_clash
          FROM product_translations t
          JOIN products p ON p.id = t.product_id
         WHERE t.language = v_langs[i] AND t.slug = v_slugs[i] AND p.status <> 'DELETED'
         LIMIT 1;
    END LOOP;

    IF v_clash IS NOT NULL THEN
        INSERT INTO bl_report (sku, outcome, detail, title) VALUES (p_sku, 'SKIPPED', v_clash, p_title_en);
        RAISE NOTICE '% SKIPPED: %', p_sku, v_clash;
        RETURN;
    END IF;

    -- 3. Category and brand.
    SELECT id INTO v_category FROM bl_category WHERE key = p_category;
    IF v_category IS NULL THEN
        RAISE EXCEPTION 'Category "%" was not resolved (needed by %). Nothing has been written.', p_category, p_sku;
    END IF;
    IF p_brand IS NOT NULL THEN
        SELECT bt.brand_id INTO v_brand
          FROM brand_translations bt
          JOIN brands b ON b.id = bt.brand_id
         WHERE upper(bt.language) = 'EN' AND lower(btrim(bt.name)) = lower(p_brand)
         ORDER BY (b.status = 'ACTIVE') DESC, b.created_at
         LIMIT 1;
        IF v_brand IS NULL THEN
            RAISE EXCEPTION 'Brand "%" not found (needed by %). Nothing has been written.', p_brand, p_sku;
        END IF;
    END IF;

    -- 4. A slug still held by a DELETED product is renamed out of the way: ProductService.freeDeletedSlug.
    FOR i IN 1..3 LOOP
        UPDATE product_translations t
           SET slug = t.slug || '-' || substr(replace(t.product_id::text, '-', ''), 1, 8)
          FROM products p
         WHERE p.id = t.product_id AND p.status = 'DELETED'
           AND t.language = v_langs[i] AND t.slug = v_slugs[i];
    END LOOP;

    -- 5. The product. Same defaults as ProductImportService.createDraftProduct: SIMPLE, DRAFT,
    --    needs_fulfilment, one unit on hand (stock_quantity and the enforced available_quantity).
    v_product := gen_random_uuid();
    INSERT INTO products (id, category_id, brand_id, product_type, is_refurbished, refurb_grade, sku,
                          availability_status, is_super_deal, is_limited_stock, stock_quantity,
                          available_quantity, status, is_active, needs_fulfilment, import_notes,
                          created_at, updated_at)
    VALUES (v_product, v_category, v_brand, 'SIMPLE', TRUE, 'A', p_sku,
            'IN_STOCK', FALSE, FALSE, 1,
            1, 'DRAFT', TRUE, TRUE, p_notes,
            now(), now());

    FOR i IN 1..3 LOOP
        INSERT INTO product_translations (id, product_id, language, title, description, slug, created_at, updated_at)
        VALUES (gen_random_uuid(), v_product, v_langs[i], v_titles[i], v_descs[i], v_slugs[i], now(), now());
    END LOOP;

    FOREACH v_spec SLICE 1 IN ARRAY p_specs LOOP
        PERFORM pg_temp.bl_spec(v_product, v_spec[1], v_spec[2], v_spec[3]);
    END LOOP;

    INSERT INTO bl_report (sku, outcome, detail, title)
    VALUES (p_sku, 'ADDED', 'draft, ' || array_length(p_specs, 1) || ' specs', p_title_en);
    RAISE NOTICE '% ADDED', p_sku;
END $fn$;


-- -----------------------------------------------------------------------------------------------------
-- 2. Categories. Laptop exists. Desktop is created unless a category of that name is already there.
-- -----------------------------------------------------------------------------------------------------
DO $$
DECLARE
    v_laptop  uuid;
    v_desktop uuid;
BEGIN
    SELECT c.id INTO v_laptop
      FROM product_categories c
      JOIN product_category_translations t ON t.category_id = c.id AND upper(t.language) = 'EN'
     WHERE t.slug = 'laptop'
     ORDER BY (c.id = 'e1a8ae86-0728-4a0b-8996-e52caed1081f') DESC
     LIMIT 1;
    IF v_laptop IS NULL THEN
        RAISE EXCEPTION 'The Laptop category (EN slug "laptop") was not found. Nothing has been written.';
    END IF;

    SELECT c.id INTO v_desktop
      FROM product_categories c
      JOIN product_category_translations t ON t.category_id = c.id AND upper(t.language) = 'EN'
     WHERE t.slug = 'desktop' OR lower(btrim(t.name)) IN ('desktop', 'desktops')
     LIMIT 1;

    IF v_desktop IS NULL THEN
        IF EXISTS (SELECT 1 FROM product_category_translations
                    WHERE (upper(language), slug) IN (('EN', 'desktop'), ('AZ', 'masaustu-komputerler'),
                                               ('AR', 'ajhiza-kumbuyutar-maktabiya'))) THEN
            RAISE EXCEPTION 'A category already uses one of the Desktop slugs. Nothing has been written.';
        END IF;
        v_desktop := gen_random_uuid();
        -- icon "tv" is the storefront's monitor-on-a-stand icon (ShopNavItem ICON_KEYS).
        INSERT INTO product_categories (id, parent_id, status, icon, created_at, updated_at)
        VALUES (v_desktop, NULL, 'ACTIVE', 'tv', now(), now());
        INSERT INTO product_category_translations (id, category_id, language, name, description, slug, created_at, updated_at)
        VALUES (gen_random_uuid(), v_desktop, 'EN', 'Desktop',
                'Desktop and all-in-one computers.', 'desktop', now(), now()),
               (gen_random_uuid(), v_desktop, 'AZ', 'Masaüstü kompüterlər',
                'Masaüstü və monoblok kompüterlər.', 'masaustu-komputerler', now(), now()),
               (gen_random_uuid(), v_desktop, 'AR', 'أجهزة كمبيوتر مكتبية',
                'أجهزة كمبيوتر مكتبية وأجهزة الكل في واحد.', 'ajhiza-kumbuyutar-maktabiya', now(), now());
        INSERT INTO bl_report (sku, outcome, detail) VALUES ('(category)', 'CREATED', 'Desktop');
        RAISE NOTICE 'Created category Desktop';
    ELSE
        INSERT INTO bl_report (sku, outcome, detail) VALUES ('(category)', 'REUSED', 'Desktop ' || v_desktop);
    END IF;

    INSERT INTO bl_category (key, id) VALUES ('laptop', v_laptop), ('desktop', v_desktop);
END $$;


-- -----------------------------------------------------------------------------------------------------
-- 3. The products.
--    Arguments: sku, category, brand, title EN/AZ/AR, slug EN/AZ/AR (built exactly as SlugUtils.toSlug
--    builds them), description EN/AZ/AR, import notes, specs as [code, value, unit].
-- -----------------------------------------------------------------------------------------------------
DO $$
BEGIN

    -- DTAX1242 · HP EliteBook x360 1030 G8 — 13.3" Convertible Touchscreen Business Laptop
    PERFORM pg_temp.bl_add(
        'DTAX1242', 'laptop', 'HP',
        'HP EliteBook x360 1030 G8 — 13.3" Convertible Touchscreen Business Laptop',
        'HP EliteBook x360 1030 G8',
        'HP EliteBook x360 1030 G8',
        'hp-elitebook-x360-1030-g8-133-convertible-touchscreen-business-laptop',
        'hp-elitebook-x360-1030-g8',
        'hp-elitebook-x360-1030-g8',
        'The HP EliteBook x360 1030 G8 is a premium 13.3-inch convertible business laptop whose touchscreen folds back so it can be used as a laptop, a tablet or in tent mode for presentations. This configuration pairs an 11th Gen Intel Core i7 processor with Intel Iris Xe graphics, 16GB RAM and a 512GB SSD, running Windows 11 Pro. It handles office work, video calls and multitasking across several applications with ease, and the compact, lightweight aluminium body makes it a good fit for people who work on the move.',
        'HP EliteBook x360 1030 G8 13.3 düymlük premium 2-si 1-də biznes noutbukudur: sensor ekranı arxaya çevrilir və cihazdan noutbuk, planşet və ya təqdimatlar üçün çadır rejimində istifadə etmək olur. Bu konfiqurasiyada 11-ci nəsil Intel Core i7 prosessoru, Intel Iris Xe qrafikası, 16GB RAM və 512GB SSD, həmçinin Windows 11 Pro təqdim olunur. Ofis işləri, video zənglər və bir neçə proqramla eyni vaxtda işləməyin öhdəsindən asanlıqla gəlir, yığcam və yüngül alüminium korpusu isə onu yolda işləyənlər üçün rahat seçimə çevirir.',
        'يُعد HP EliteBook x360 1030 G8 حاسوبًا محمولًا فاخرًا للأعمال بشاشة لمس مقاس 13.3 بوصة قابلة للطي بالكامل، ما يتيح استخدامه كحاسوب محمول أو كجهاز لوحي أو في وضع الخيمة للعروض التقديمية. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الحادي عشر مع رسومات Intel Iris Xe وذاكرة 16GB RAM ووحدة تخزين 512GB SSD ونظام Windows 11 Pro. ويتعامل بسهولة مع الأعمال المكتبية ومكالمات الفيديو وتعدد المهام بين عدة برامج، فيما يجعله هيكله المدمج والخفيف المصنوع من الألومنيوم خيارًا مناسبًا لمن يعملون أثناء التنقل.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: HP EliteBook X360 1030 G8 | Core i7 | 11th | 16GB RAM | 512 GB SSD | Windows 11pro | 512GB SSD
Sheet price: 1899

Check before publishing:
- Touchscreen, 13.3" screen and Iris Xe graphics come from the model''s published specs (every x360 1030 G8 has them); the sheet does not list them.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['gpu', 'Intel Iris Xe', NULL], ['display', '13.3"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1224 · HP ZBook Fury 16 G9 — 16" Mobile Workstation with 8GB NVIDIA RTX Graphics
    PERFORM pg_temp.bl_add(
        'DTAX1224', 'laptop', 'HP',
        'HP ZBook Fury 16 G9 — 16" Mobile Workstation with 8GB NVIDIA RTX Graphics',
        'HP ZBook Fury 16 G9',
        'HP ZBook Fury 16 G9',
        'hp-zbook-fury-16-g9-16-mobile-workstation-with-8gb-nvidia-rtx-graphics',
        'hp-zbook-fury-16-g9',
        'hp-zbook-fury-16-g9',
        'The HP ZBook Fury 16 G9 is a 16-inch mobile workstation built for engineers, designers and content creators who need desktop-class performance they can carry. This configuration combines a 12th Gen Intel Core i7 processor with 8GB of dedicated NVIDIA RTX graphics, 16GB RAM and a 512GB SSD, running Windows 11 Pro. The dedicated graphics accelerate CAD, 3D modelling, rendering and video editing, while the large 16-inch screen gives plenty of room for timelines, drawings and multiple windows.',
        'HP ZBook Fury 16 G9 masaüstü səviyyəli performansı özü ilə daşımaq istəyən mühəndislər, dizaynerlər və kontent yaradıcıları üçün hazırlanmış 16 düymlük mobil iş stansiyasıdır. Bu konfiqurasiyada 12-ci nəsil Intel Core i7 prosessoru, 8GB həcmli diskret NVIDIA RTX qrafika kartı, 16GB RAM və 512GB SSD, həmçinin Windows 11 Pro təqdim olunur. Diskret qrafika CAD, 3D modelləşdirmə, render və video montaj işlərini sürətləndirir, böyük 16 düymlük ekran isə zaman xətləri, çertyojlar və bir neçə pəncərə üçün geniş iş sahəsi yaradır.',
        'يُعد HP ZBook Fury 16 G9 محطة عمل محمولة بشاشة مقاس 16 بوصة، مصممة للمهندسين والمصممين وصنّاع المحتوى الذين يحتاجون إلى أداء يضاهي الحواسيب المكتبية أينما ذهبوا. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الثاني عشر مع بطاقة رسومات NVIDIA RTX مخصصة بسعة 8GB وذاكرة 16GB RAM ووحدة تخزين 512GB SSD ونظام Windows 11 Pro. وتعمل بطاقة الرسومات المخصصة على تسريع برامج CAD والنمذجة ثلاثية الأبعاد والتصيير وتحرير الفيديو، بينما توفر الشاشة الكبيرة مقاس 16 بوصة مساحة واسعة للخطوط الزمنية والرسومات والنوافذ المتعددة.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: HP FURY G9 | Intel Core i7 | 12th Gen | 16GB RAM | 512 GB SSD | 8GB GPU | Windows 11 Pro | 512GB SSD
Sheet price: 3499

Check before publishing:
- Sheet says ''HP FURY G9''; listed as the HP ZBook Fury 16 G9, the only Fury G9 HP makes. 16" screen from the model''s specs. The only 8GB GPU on this model is the NVIDIA RTX A2000; the copy says ''NVIDIA RTX'' without naming it.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['gpu', '8', 'GB'], ['display', '16"', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1569 · Dell XPS 13 9370 — Compact 13.3" 4K Touchscreen Ultrabook
    PERFORM pg_temp.bl_add(
        'DTAX1569', 'laptop', 'Dell',
        'Dell XPS 13 9370 — Compact 13.3" 4K Touchscreen Ultrabook',
        'Dell XPS 13 9370',
        'Dell XPS 13 9370',
        'dell-xps-13-9370-compact-133-4k-touchscreen-ultrabook',
        'dell-xps-13-9370',
        'dell-xps-13-9370',
        'The Dell XPS 13 9370 is a premium ultrabook with a 13.3-inch 4K Ultra HD touchscreen set in Dell''s slim InfinityEdge bezels, which keep the machine compact for its screen size. This configuration has an 8th Gen Intel Core i7 processor, 16GB RAM and a 512GB SSD, running Windows 11 Pro. It is well suited to office work, browsing, streaming and light photo editing, and the sharp touchscreen makes reading, scrolling and zooming feel natural.',
        'Dell XPS 13 9370 nazik InfinityEdge çərçivələrinə malik 13.3 düymlük 4K Ultra HD sensor ekranlı premium ultrabukdur; bu çərçivələr sayəsində cihaz ekran ölçüsünə görə olduqca yığcamdır. Bu konfiqurasiyada 8-ci nəsil Intel Core i7 prosessoru, 16GB RAM və 512GB SSD, həmçinin Windows 11 Pro təqdim olunur. Ofis işləri, internetdə gəzinti, video izləmə və yüngül foto redaktəsi üçün uyğundur, yüksək dəqiqlikli sensor ekran isə oxumağı, sürüşdürməyi və yaxınlaşdırmağı rahatlaşdırır.',
        'يُعد Dell XPS 13 9370 حاسوبًا محمولًا فائق النحافة من الفئة الممتازة، بشاشة لمس 4K Ultra HD مقاس 13.3 بوصة محاطة بإطارات InfinityEdge الرفيعة التي تجعل الجهاز صغير الحجم مقارنةً بحجم شاشته. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الثامن وذاكرة 16GB RAM ووحدة تخزين 512GB SSD ونظام Windows 11 Pro. وهو مناسب للأعمال المكتبية وتصفح الإنترنت ومشاهدة الفيديو وتحرير الصور البسيط، كما تجعل شاشة اللمس عالية الدقة القراءة والتمرير والتكبير أكثر سهولة.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: DELL XPS 13 9370 | Intel Core i7 | 8th Gen | 16GB RAM | 512GB SSD | TOUCH | Windows 11 pro
Sheet price: 1499

Check before publishing:
- On the XPS 13 9370 the touch panel is always the 4K UHD one (the FHD panel is non-touch), so the copy says 4K.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['display', '13.3"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX0865 · Lenovo ThinkPad L14 Gen 1 — 14" Touchscreen Business Laptop, Core i5, 8GB RAM, 256GB SSD
    PERFORM pg_temp.bl_add(
        'DTAX0865', 'laptop', 'Lenovo',
        'Lenovo ThinkPad L14 Gen 1 — 14" Touchscreen Business Laptop, Core i5, 8GB RAM, 256GB SSD',
        'Lenovo ThinkPad L14 Gen 1 (Core i5, 8GB, 256GB, sensor ekran)',
        'Lenovo ThinkPad L14 Gen 1 (Core i5، 8GB، 256GB، شاشة لمس)',
        'lenovo-thinkpad-l14-gen-1-14-touchscreen-business-laptop-core-i5-8gb-ram-256gb-ssd',
        'lenovo-thinkpad-l14-gen-1-core-i5-8gb-256gb-sensor-ekran',
        'lenovo-thinkpad-l14-gen-1-core-i5-8gb-256gb-shasha-lms',
        'The Lenovo ThinkPad L14 Gen 1 is a 14-inch business laptop with the durable build and comfortable keyboard the ThinkPad series is known for. This configuration has a 10th Gen Intel Core i5 processor, 8GB RAM and a 256GB SSD, together with a touchscreen, a black finish and Windows 11 Pro. It comfortably handles office applications, web browsing, video calls and online study, and the touchscreen adds a quick, hands-on way to scroll, zoom and navigate.',
        'Lenovo ThinkPad L14 Gen 1 ThinkPad seriyasının tanındığı möhkəm korpusa və rahat klaviaturaya malik 14 düymlük biznes noutbukudur. Bu konfiqurasiyada 10-cu nəsil Intel Core i5 prosessoru, 8GB RAM və 256GB SSD, həmçinin sensor ekran, qara rəngli korpus və Windows 11 Pro təqdim olunur. Ofis proqramları, internetdə gəzinti, video zənglər və onlayn təhsil üçün rahatlıqla istifadə olunur, sensor ekran isə toxunuşla sürüşdürmə, yaxınlaşdırma və naviqasiya imkanı əlavə edir.',
        'يُعد Lenovo ThinkPad L14 Gen 1 حاسوبًا محمولًا للأعمال بشاشة مقاس 14 بوصة، يتميز بالهيكل المتين ولوحة المفاتيح المريحة التي تشتهر بها سلسلة ThinkPad. تأتي هذه النسخة بمعالج Intel Core i5 من الجيل العاشر وذاكرة 8GB RAM ووحدة تخزين 256GB SSD، إلى جانب شاشة لمس ولون أسود ونظام Windows 11 Pro. ويتعامل بسهولة مع التطبيقات المكتبية وتصفح الإنترنت ومكالمات الفيديو والدراسة عبر الإنترنت، فيما تضيف شاشة اللمس طريقة مباشرة للتمرير والتكبير والتنقل.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Lenovo L14 | Intel Core i5 | 10th Gen | 8GB RAM | 256 GB SSD | Touch | Black | Windows 11 Pro | 256GB SSD
Sheet price: 899

Check before publishing:
- Sheet says ''Lenovo L14'' with a 10th Gen CPU, which is the ThinkPad L14 Gen 1. 14" from the model''s specs.
- Four L14 Gen 1 units in this batch, so the AZ/AR titles carry the configuration to stay unique.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i5', NULL], ['ram', '8', 'GB'], ['storage', '256', 'GB'], ['display', '14"', NULL], ['touchable_screen', 'Yes', NULL], ['color', 'Black', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1536 · Lenovo ThinkPad P15v Gen 3 — 15.6" Mobile Workstation, 32GB RAM, 256GB SSD
    PERFORM pg_temp.bl_add(
        'DTAX1536', 'laptop', 'Lenovo',
        'Lenovo ThinkPad P15v Gen 3 — 15.6" Mobile Workstation, 32GB RAM, 256GB SSD',
        'Lenovo ThinkPad P15v Gen 3 (32GB, 256GB)',
        'Lenovo ThinkPad P15v Gen 3 (32GB، 256GB)',
        'lenovo-thinkpad-p15v-gen-3-156-mobile-workstation-32gb-ram-256gb-ssd',
        'lenovo-thinkpad-p15v-gen-3-32gb-256gb',
        'lenovo-thinkpad-p15v-gen-3-32gb-256gb',
        'The Lenovo ThinkPad P15v Gen 3 is a 15.6-inch mobile workstation that brings professional graphics performance to engineers, architects and designers in a ThinkPad body. This configuration has a 12th Gen Intel Core i7 processor, 4GB of dedicated NVIDIA graphics, a generous 32GB of RAM and a 256GB SSD, running Windows 11 Pro. The 32GB of memory keeps large CAD models, datasets and many open applications responsive, and the dedicated graphics speed up 3D work, rendering and video editing.',
        'Lenovo ThinkPad P15v Gen 3 mühəndislər, memarlar və dizaynerlər üçün peşəkar qrafika performansını ThinkPad korpusunda təqdim edən 15.6 düymlük mobil iş stansiyasıdır. Bu konfiqurasiyada 12-ci nəsil Intel Core i7 prosessoru, 4GB həcmli diskret NVIDIA qrafika kartı, geniş 32GB RAM və 256GB SSD, həmçinin Windows 11 Pro təqdim olunur. 32GB yaddaş böyük CAD modelləri, məlumat massivləri və çoxlu açıq proqramla işləyərkən sürəti qoruyur, diskret qrafika isə 3D işləri, render və video montajı sürətləndirir.',
        'يُعد Lenovo ThinkPad P15v Gen 3 محطة عمل محمولة بشاشة مقاس 15.6 بوصة، تقدم أداءً رسوميًا احترافيًا للمهندسين والمعماريين والمصممين في هيكل ThinkPad. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الثاني عشر مع بطاقة رسومات NVIDIA مخصصة بسعة 4GB وذاكرة كبيرة بسعة 32GB RAM ووحدة تخزين 256GB SSD ونظام Windows 11 Pro. وتحافظ ذاكرة 32GB على سرعة الاستجابة عند العمل على نماذج CAD الكبيرة ومجموعات البيانات والعديد من البرامج المفتوحة، بينما تعمل بطاقة الرسومات المخصصة على تسريع أعمال التصميم ثلاثي الأبعاد والتصيير وتحرير الفيديو.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Lenovo P15V | Intel Core i7 | 12th Gen | 32GB RAM | 256 GB SSD | 4GB GPU | Windows 11 Pro | 256GB SSD
Sheet price: 2999

Check before publishing:
- Sheet says ''P15V'' with a 12th Gen CPU, which is the ThinkPad P15v Gen 3. 15.6" screen and NVIDIA graphics from the model''s specs (every GPU option on the Intel P15v Gen 3 is NVIDIA).
- Two P15v Gen 3 units in this batch, so the AZ/AR titles carry RAM/SSD to stay unique.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '32', 'GB'], ['storage', '256', 'GB'], ['gpu', '4', 'GB'], ['display', '15.6"', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1066 · Lenovo ThinkPad L14 Gen 1 — 14" Touchscreen Business Laptop, Core i7, 8GB RAM, 256GB SSD
    PERFORM pg_temp.bl_add(
        'DTAX1066', 'laptop', 'Lenovo',
        'Lenovo ThinkPad L14 Gen 1 — 14" Touchscreen Business Laptop, Core i7, 8GB RAM, 256GB SSD',
        'Lenovo ThinkPad L14 Gen 1 (Core i7, 8GB, 256GB, sensor ekran)',
        'Lenovo ThinkPad L14 Gen 1 (Core i7، 8GB، 256GB، شاشة لمس)',
        'lenovo-thinkpad-l14-gen-1-14-touchscreen-business-laptop-core-i7-8gb-ram-256gb-ssd',
        'lenovo-thinkpad-l14-gen-1-core-i7-8gb-256gb-sensor-ekran',
        'lenovo-thinkpad-l14-gen-1-core-i7-8gb-256gb-shasha-lms',
        'The Lenovo ThinkPad L14 Gen 1 is a 14-inch business laptop built with the sturdy chassis and comfortable keyboard ThinkPads are known for. This configuration has a 10th Gen Intel Core i7 processor, 8GB RAM and a 256GB SSD, together with a touchscreen, a black finish and Windows 11 Pro. The Core i7 processor keeps office applications, video calls and multitasking responsive, and the touchscreen gives a quick, hands-on way to scroll, zoom and navigate.',
        'Lenovo ThinkPad L14 Gen 1 ThinkPad-ların tanındığı möhkəm korpus və rahat klaviatura ilə hazırlanmış 14 düymlük biznes noutbukudur. Bu konfiqurasiyada 10-cu nəsil Intel Core i7 prosessoru, 8GB RAM və 256GB SSD, həmçinin sensor ekran, qara rəngli korpus və Windows 11 Pro təqdim olunur. Core i7 prosessoru ofis proqramları, video zənglər və çoxsaylı tapşırıqlar zamanı sürəti qoruyur, sensor ekran isə toxunuşla sürüşdürmə, yaxınlaşdırma və naviqasiya imkanı verir.',
        'يُعد Lenovo ThinkPad L14 Gen 1 حاسوبًا محمولًا للأعمال بشاشة مقاس 14 بوصة، يتميز بالهيكل المتين ولوحة المفاتيح المريحة التي تشتهر بها أجهزة ThinkPad. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل العاشر وذاكرة 8GB RAM ووحدة تخزين 256GB SSD، إلى جانب شاشة لمس ولون أسود ونظام Windows 11 Pro. ويحافظ معالج Core i7 على سرعة الاستجابة في التطبيقات المكتبية ومكالمات الفيديو وتعدد المهام، فيما توفر شاشة اللمس طريقة مباشرة للتمرير والتكبير والتنقل.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Lenovo L14 Gen 1 | Intel Core i7 | 10th Gen | 8GB RAM | 256 GB SSD | TOUCH | Black | Windows 11pro | 256GB SSD
Sheet price: 999

Check before publishing:
- 14" from the model''s specs.
- Four L14 Gen 1 units in this batch, so the AZ/AR titles carry the configuration to stay unique.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '8', 'GB'], ['storage', '256', 'GB'], ['display', '14"', NULL], ['touchable_screen', 'Yes', NULL], ['color', 'Black', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1039 · Lenovo ThinkPad L14 Gen 1 — 14" Business Laptop, Core i7, 8GB RAM, 256GB SSD
    PERFORM pg_temp.bl_add(
        'DTAX1039', 'laptop', 'Lenovo',
        'Lenovo ThinkPad L14 Gen 1 — 14" Business Laptop, Core i7, 8GB RAM, 256GB SSD',
        'Lenovo ThinkPad L14 Gen 1 (Core i7, 8GB, 256GB)',
        'Lenovo ThinkPad L14 Gen 1 (Core i7، 8GB، 256GB)',
        'lenovo-thinkpad-l14-gen-1-14-business-laptop-core-i7-8gb-ram-256gb-ssd',
        'lenovo-thinkpad-l14-gen-1-core-i7-8gb-256gb',
        'lenovo-thinkpad-l14-gen-1-core-i7-8gb-256gb',
        'The Lenovo ThinkPad L14 Gen 1 is a 14-inch business laptop that pairs a durable ThinkPad build with the comfortable keyboard the series is known for. This configuration has a 10th Gen Intel Core i7 processor, 8GB RAM and a 256GB SSD, with a black finish and Windows 11 Pro. It is a dependable everyday machine for office applications, web browsing, video calls and online study, and the SSD keeps start-up and file access quick.',
        'Lenovo ThinkPad L14 Gen 1 möhkəm ThinkPad korpusunu seriyanın tanındığı rahat klaviatura ilə birləşdirən 14 düymlük biznes noutbukudur. Bu konfiqurasiyada 10-cu nəsil Intel Core i7 prosessoru, 8GB RAM və 256GB SSD, həmçinin qara rəngli korpus və Windows 11 Pro təqdim olunur. Ofis proqramları, internetdə gəzinti, video zənglər və onlayn təhsil üçün etibarlı gündəlik kompüterdir, SSD isə sistemin açılmasını və fayllara girişi sürətli edir.',
        'يُعد Lenovo ThinkPad L14 Gen 1 حاسوبًا محمولًا للأعمال بشاشة مقاس 14 بوصة، يجمع بين متانة هيكل ThinkPad ولوحة المفاتيح المريحة التي تشتهر بها السلسلة. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل العاشر وذاكرة 8GB RAM ووحدة تخزين 256GB SSD، مع لون أسود ونظام Windows 11 Pro. وهو جهاز يومي موثوق للتطبيقات المكتبية وتصفح الإنترنت ومكالمات الفيديو والدراسة عبر الإنترنت، فيما تحافظ وحدة SSD على سرعة التشغيل والوصول إلى الملفات.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Lenovo L14 Gen 1 | Intel Core i7 | 10th Gen | 8GB RAM | 256 GB SSD | Black | Windows 11pro | 256GB SSD
Sheet price: 999

Check before publishing:
- 14" from the model''s specs. No touchscreen on the sheet, so none is claimed.
- Four L14 Gen 1 units in this batch, so the AZ/AR titles carry the configuration to stay unique.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '8', 'GB'], ['storage', '256', 'GB'], ['display', '14"', NULL], ['color', 'Black', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1267 · Apple iMac 27" Retina 5K 2017 — All-in-One Desktop, Core i7, 16GB RAM, 1TB SSD
    PERFORM pg_temp.bl_add(
        'DTAX1267', 'desktop', 'Apple',
        'Apple iMac 27" Retina 5K 2017 — All-in-One Desktop, Core i7, 16GB RAM, 1TB SSD',
        'Apple iMac 27" Retina 5K 2017 (Core i7)',
        'Apple iMac 27" Retina 5K 2017 (Core i7)',
        'apple-imac-27-retina-5k-2017-all-in-one-desktop-core-i7-16gb-ram-1tb-ssd',
        'apple-imac-27-retina-5k-2017-core-i7',
        'apple-imac-27-retina-5k-2017-core-i7',
        'The Apple iMac 27-inch (2017) is an all-in-one desktop with a 27-inch Retina 5K display (5120 × 2880), with the whole computer built into the slim aluminium body behind the screen. This configuration has an Intel Core i7 processor, 8GB of dedicated AMD Radeon Pro graphics, 16GB RAM and a 1TB SSD, running macOS. The high-resolution screen and dedicated graphics make it well suited to photo and video editing, design work and everyday productivity, and the 1TB SSD leaves plenty of room for large media libraries.',
        'Apple iMac 27 düym (2017) 27 düymlük Retina 5K (5120 × 2880) ekrana malik monoblok kompüterdir: bütün kompüter ekranın arxasındakı nazik alüminium korpusda yerləşir. Bu konfiqurasiyada Intel Core i7 prosessoru, 8GB həcmli diskret AMD Radeon Pro qrafika kartı, 16GB RAM və 1TB SSD, həmçinin macOS əməliyyat sistemi təqdim olunur. Yüksək dəqiqlikli ekran və diskret qrafika onu foto və video montajı, dizayn işləri və gündəlik məhsuldarlıq üçün uyğun edir, 1TB SSD isə böyük media kitabxanaları üçün geniş yer ayırır.',
        'يُعد Apple iMac مقاس 27 بوصة (2017) حاسوبًا مكتبيًا متكاملًا (الكل في واحد) بشاشة Retina 5K مقاس 27 بوصة (5120 × 2880)، حيث يُدمج الحاسوب بالكامل في هيكل الألومنيوم النحيف خلف الشاشة. تأتي هذه النسخة بمعالج Intel Core i7 مع بطاقة رسومات AMD Radeon Pro مخصصة بسعة 8GB وذاكرة 16GB RAM ووحدة تخزين 1TB SSD ونظام macOS. وتجعله الشاشة عالية الدقة وبطاقة الرسومات المخصصة مناسبًا لتحرير الصور والفيديو وأعمال التصميم والإنتاجية اليومية، فيما توفر وحدة 1TB SSD مساحة واسعة لمكتبات الوسائط الكبيرة.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: IMAC 2017 | CORE I7 | 16GB RAM | 1 TB SSD | 8GB GPU | 27" INCH | 1TB SSD
Sheet price: 2599

Check before publishing:
- Retina 5K (5120 x 2880) and AMD Radeon Pro graphics come from Apple''s specs for the 2017 27" iMac (every one has them; 8GB is the Radeon Pro 580). macOS is not on the sheet but is what the machine runs.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '1', 'TB'], ['gpu', '8', 'GB'], ['display', '27"', NULL], ['operating_system', 'macOS', NULL]]::text[]);
    -- DTAX1002 · Chromebook — Lightweight Laptop for Browsing and Study, 4GB RAM, 32GB Storage
    PERFORM pg_temp.bl_add(
        'DTAX1002', 'laptop', NULL,
        'Chromebook — Lightweight Laptop for Browsing and Study, 4GB RAM, 32GB Storage',
        'Chromebook (4GB, 32GB)',
        'Chromebook (4GB، 32GB)',
        'chromebook-lightweight-laptop-for-browsing-and-study-4gb-ram-32gb-storage',
        'chromebook-4gb-32gb',
        'chromebook-4gb-32gb',
        'This Chromebook is a simple, affordable laptop running ChromeOS, built around the web and Google apps such as Gmail, Docs and Drive. It has 4GB RAM and 32GB of built-in storage, with documents and photos usually kept in Google Drive rather than on the device. It is a practical choice for students, online classes, browsing, email and video calls, and ChromeOS starts up in seconds.',
        'Bu Chromebook veb və Gmail, Docs və Drive kimi Google tətbiqləri üzərində qurulmuş ChromeOS ilə işləyən sadə və sərfəli noutbukdur. 4GB RAM və 32GB daxili yaddaşa malikdir, sənəd və fotolar isə adətən cihazda deyil, Google Drive-da saxlanılır. Tələbələr, onlayn dərslər, internetdə gəzinti, elektron poçt və video zənglər üçün praktik seçimdir, ChromeOS isə bir neçə saniyəyə açılır.',
        'يُعد هذا الـ Chromebook حاسوبًا محمولًا بسيطًا وبسعر مناسب يعمل بنظام ChromeOS، المصمم حول الويب وتطبيقات Google مثل Gmail وDocs وDrive. يأتي بذاكرة 4GB RAM ووحدة تخزين داخلية بسعة 32GB، وعادةً ما تُحفظ المستندات والصور على Google Drive بدلًا من الجهاز. وهو خيار عملي للطلاب والدروس عبر الإنترنت وتصفح الويب والبريد الإلكتروني ومكالمات الفيديو، كما يعمل نظام ChromeOS خلال ثوانٍ.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: CHROMEBOOK 4GB 32 GB | 4GB RAM
Sheet price: 199

Check before publishing:
- BRAND, MODEL, PROCESSOR AND SCREEN SIZE ARE NOT ON THE SHEET. The title is generic and no brand is set - fill these in from the unit before publishing.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['ram', '4', 'GB'], ['storage', '32', 'GB'], ['operating_system', 'ChromeOS', NULL]]::text[]);
    -- DTAX0126 · Dell Latitude 5320 — Compact 13.3" Business Laptop with 11th Gen Intel Core i5
    PERFORM pg_temp.bl_add(
        'DTAX0126', 'laptop', 'Dell',
        'Dell Latitude 5320 — Compact 13.3" Business Laptop with 11th Gen Intel Core i5',
        'Dell Latitude 5320',
        'Dell Latitude 5320',
        'dell-latitude-5320-compact-133-business-laptop-with-11th-gen-intel-core-i5',
        'dell-latitude-5320',
        'dell-latitude-5320',
        'The Dell Latitude 5320 is a compact 13.3-inch business laptop, light enough to carry every day and built for all-day work. This configuration has an 11th Gen Intel Core i5 processor with Intel Iris Xe graphics, 16GB RAM and a 256GB SSD, running Windows 11 Pro. With 16GB of memory it stays responsive with many browser tabs, office applications and video calls open at once, making it a dependable machine for professionals and students.',
        'Dell Latitude 5320 hər gün özünüzlə daşımaq üçün kifayət qədər yüngül olan və bütün gün iş üçün nəzərdə tutulmuş 13.3 düymlük yığcam biznes noutbukudur. Bu konfiqurasiyada Intel Iris Xe qrafikalı 11-ci nəsil Intel Core i5 prosessoru, 16GB RAM və 256GB SSD, həmçinin Windows 11 Pro təqdim olunur. 16GB yaddaş sayəsində çoxlu brauzer pəncərəsi, ofis proqramları və video zənglər eyni vaxtda açıq olduqda belə sürətli işləyir, bu da onu mütəxəssislər və tələbələr üçün etibarlı seçim edir.',
        'يُعد Dell Latitude 5320 حاسوبًا محمولًا مدمجًا للأعمال بشاشة مقاس 13.3 بوصة، خفيف بما يكفي لحمله يوميًا ومصمم للعمل طوال اليوم. تأتي هذه النسخة بمعالج Intel Core i5 من الجيل الحادي عشر مع رسومات Intel Iris Xe وذاكرة 16GB RAM ووحدة تخزين 256GB SSD ونظام Windows 11 Pro. وبفضل ذاكرة 16GB يبقى سريع الاستجابة حتى مع فتح العديد من علامات تبويب المتصفح والتطبيقات المكتبية ومكالمات الفيديو في الوقت نفسه، ما يجعله خيارًا موثوقًا للمحترفين والطلاب.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Dell 5320 | Intel Core i5 | 11th Gen | 16GB RAM | 256GB SSD | Windows 11 Pro
Sheet price: 999

Check before publishing:
- 13.3" screen and Iris Xe graphics from the model''s specs.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i5', NULL], ['ram', '16', 'GB'], ['storage', '256', 'GB'], ['gpu', 'Intel Iris Xe', NULL], ['display', '13.3"', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1266 · Apple iMac 27" Retina 5K 2017 — All-in-One Desktop, Core i5, 16GB RAM, 250GB SSD
    PERFORM pg_temp.bl_add(
        'DTAX1266', 'desktop', 'Apple',
        'Apple iMac 27" Retina 5K 2017 — All-in-One Desktop, Core i5, 16GB RAM, 250GB SSD',
        'Apple iMac 27" Retina 5K 2017 (Core i5)',
        'Apple iMac 27" Retina 5K 2017 (Core i5)',
        'apple-imac-27-retina-5k-2017-all-in-one-desktop-core-i5-16gb-ram-250gb-ssd',
        'apple-imac-27-retina-5k-2017-core-i5',
        'apple-imac-27-retina-5k-2017-core-i5',
        'The Apple iMac 27-inch (2017) is an all-in-one desktop with a 27-inch Retina 5K display (5120 × 2880), with the whole computer built into the slim aluminium body behind the screen. This configuration has an Intel Core i5 processor, 4GB of dedicated AMD Radeon Pro graphics, 16GB RAM and a 250GB SSD, running macOS. It is a good fit for home offices, study and creative work, and the large, sharp screen shows photos, documents and video calls in fine detail.',
        'Apple iMac 27 düym (2017) 27 düymlük Retina 5K (5120 × 2880) ekrana malik monoblok kompüterdir: bütün kompüter ekranın arxasındakı nazik alüminium korpusda yerləşir. Bu konfiqurasiyada Intel Core i5 prosessoru, 4GB həcmli diskret AMD Radeon Pro qrafika kartı, 16GB RAM və 250GB SSD, həmçinin macOS əməliyyat sistemi təqdim olunur. Ev ofisi, təhsil və yaradıcı işlər üçün uyğundur, böyük və aydın ekran isə fotoları, sənədləri və video zəngləri yüksək keyfiyyətdə göstərir.',
        'يُعد Apple iMac مقاس 27 بوصة (2017) حاسوبًا مكتبيًا متكاملًا (الكل في واحد) بشاشة Retina 5K مقاس 27 بوصة (5120 × 2880)، حيث يُدمج الحاسوب بالكامل في هيكل الألومنيوم النحيف خلف الشاشة. تأتي هذه النسخة بمعالج Intel Core i5 مع بطاقة رسومات AMD Radeon Pro مخصصة بسعة 4GB وذاكرة 16GB RAM ووحدة تخزين 250GB SSD ونظام macOS. وهو مناسب للمكاتب المنزلية والدراسة والأعمال الإبداعية، فيما تعرض الشاشة الكبيرة والواضحة الصور والمستندات ومكالمات الفيديو بجودة عالية.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: IMAC 2017 | CORE I5 | 16GB RAM | 250GB SSD | 4GB GPU | 27" INCH
Sheet price: 2399

Check before publishing:
- Retina 5K (5120 x 2880) and AMD Radeon Pro graphics come from Apple''s specs for the 2017 27" iMac. macOS is not on the sheet but is what the machine runs.
- 250GB is not an Apple factory size - probably a replacement SSD. Listed as the sheet says.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i5', NULL], ['ram', '16', 'GB'], ['storage', '250', 'GB'], ['gpu', '4', 'GB'], ['display', '27"', NULL], ['operating_system', 'macOS', NULL]]::text[]);
    -- DTAX0036 · Apple iMac 21.5" Retina 4K 2017 — All-in-One Desktop, Core i5, 8GB RAM, 1TB SSD
    PERFORM pg_temp.bl_add(
        'DTAX0036', 'desktop', 'Apple',
        'Apple iMac 21.5" Retina 4K 2017 — All-in-One Desktop, Core i5, 8GB RAM, 1TB SSD',
        'Apple iMac 21.5" Retina 4K 2017',
        'Apple iMac 21.5" Retina 4K 2017',
        'apple-imac-215-retina-4k-2017-all-in-one-desktop-core-i5-8gb-ram-1tb-ssd',
        'apple-imac-215-retina-4k-2017',
        'apple-imac-215-retina-4k-2017',
        'The Apple iMac 21.5-inch (2017) is a compact all-in-one desktop with a 21.5-inch Retina 4K display (4096 × 2304), with the whole computer built into the slim aluminium body behind the screen. This configuration has an Intel Core i5 processor, 2GB of dedicated AMD Radeon Pro graphics, 8GB RAM and a 1TB SSD, running macOS. It suits home and office desks where space is limited, handling everyday work, study, photo editing and video calls, and the 1TB SSD offers generous room for files and photo libraries.',
        'Apple iMac 21.5 düym (2017) 21.5 düymlük Retina 4K (4096 × 2304) ekrana malik yığcam monoblok kompüterdir: bütün kompüter ekranın arxasındakı nazik alüminium korpusda yerləşir. Bu konfiqurasiyada Intel Core i5 prosessoru, 2GB həcmli diskret AMD Radeon Pro qrafika kartı, 8GB RAM və 1TB SSD, həmçinin macOS əməliyyat sistemi təqdim olunur. Yeri məhdud olan ev və ofis masaları üçün uyğundur: gündəlik iş, təhsil, foto redaktəsi və video zənglərin öhdəsindən gəlir, 1TB SSD isə fayllar və foto kitabxanaları üçün geniş yer ayırır.',
        'يُعد Apple iMac مقاس 21.5 بوصة (2017) حاسوبًا مكتبيًا متكاملًا (الكل في واحد) صغير الحجم بشاشة Retina 4K مقاس 21.5 بوصة (4096 × 2304)، حيث يُدمج الحاسوب بالكامل في هيكل الألومنيوم النحيف خلف الشاشة. تأتي هذه النسخة بمعالج Intel Core i5 مع بطاقة رسومات AMD Radeon Pro مخصصة بسعة 2GB وذاكرة 8GB RAM ووحدة تخزين 1TB SSD ونظام macOS. وهو مناسب للمكاتب المنزلية والمكتبية ذات المساحة المحدودة، إذ يتعامل مع الأعمال اليومية والدراسة وتحرير الصور ومكالمات الفيديو، فيما توفر وحدة 1TB SSD مساحة واسعة للملفات ومكتبات الصور.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: IMAC 2017 | Core i5 | 8GB RAM | 1TB SSD | 21.5" Inch | 2GB GPU
Sheet price: 1399

Check before publishing:
- A 2017 21.5" iMac with a 2GB GPU is the Retina 4K model (4096 x 2304, AMD Radeon Pro 555); the non-Retina one has no dedicated GPU. macOS is not on the sheet but is what the machine runs.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i5', NULL], ['ram', '8', 'GB'], ['storage', '1', 'TB'], ['gpu', '2', 'GB'], ['display', '21.5"', NULL], ['operating_system', 'macOS', NULL]]::text[]);
    -- DTAX1359 · Dell Latitude 7440 2-in-1 — 14" Convertible Touchscreen Business Laptop
    PERFORM pg_temp.bl_add(
        'DTAX1359', 'laptop', 'Dell',
        'Dell Latitude 7440 2-in-1 — 14" Convertible Touchscreen Business Laptop',
        'Dell Latitude 7440 2-in-1',
        'Dell Latitude 7440 2-in-1',
        'dell-latitude-7440-2-in-1-14-convertible-touchscreen-business-laptop',
        'dell-latitude-7440-2-in-1',
        'dell-latitude-7440-2-in-1',
        'The Dell Latitude 7440 2-in-1 is a 14-inch premium business convertible whose touchscreen rotates 360 degrees, so it works as a laptop, a tablet or in tent mode for presentations. This configuration has a 13th Gen Intel Core i7 processor with Intel Iris Xe graphics, 16GB RAM and a 512GB SSD, running Windows 11 Pro. It handles demanding office work, video conferencing and multitasking with ease, and the 14-inch touchscreen is comfortable for reading, reviewing documents and presenting.',
        'Dell Latitude 7440 2-in-1 sensor ekranı 360 dərəcə fırlanan 14 düymlük premium 2-si 1-də biznes noutbukudur: ondan noutbuk, planşet və ya təqdimatlar üçün çadır rejimində istifadə etmək olur. Bu konfiqurasiyada Intel Iris Xe qrafikalı 13-cü nəsil Intel Core i7 prosessoru, 16GB RAM və 512GB SSD, həmçinin Windows 11 Pro təqdim olunur. Ağır ofis işlərinin, video konfransların və çoxsaylı tapşırıqların öhdəsindən asanlıqla gəlir, 14 düymlük sensor ekran isə sənədləri oxumaq, nəzərdən keçirmək və təqdimat etmək üçün rahatdır.',
        'يُعد Dell Latitude 7440 2-in-1 حاسوبًا محمولًا فاخرًا للأعمال قابلًا للتحويل بشاشة لمس مقاس 14 بوصة تدور بزاوية 360 درجة، ما يتيح استخدامه كحاسوب محمول أو كجهاز لوحي أو في وضع الخيمة للعروض التقديمية. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الثالث عشر مع رسومات Intel Iris Xe وذاكرة 16GB RAM ووحدة تخزين 512GB SSD ونظام Windows 11 Pro. ويتعامل بسهولة مع الأعمال المكتبية المكثفة ومؤتمرات الفيديو وتعدد المهام، فيما تُعد شاشة اللمس مقاس 14 بوصة مريحة لقراءة المستندات ومراجعتها وتقديم العروض.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: DELL 7440 | (2 IN 1) | Intel Core i7 | 13th GEN | 16GB RAM | 512GB SSD | Touch | Windows 11 Pro
Sheet price: 2199

Check before publishing:
- 14" screen and Iris Xe graphics from the model''s specs.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['gpu', 'Intel Iris Xe', NULL], ['display', '14"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX0836 · Lenovo ThinkPad L14 Gen 1 — 14" Touchscreen Business Laptop, Core i7, 16GB RAM, 512GB SSD
    PERFORM pg_temp.bl_add(
        'DTAX0836', 'laptop', 'Lenovo',
        'Lenovo ThinkPad L14 Gen 1 — 14" Touchscreen Business Laptop, Core i7, 16GB RAM, 512GB SSD',
        'Lenovo ThinkPad L14 Gen 1 (Core i7, 16GB, 512GB, sensor ekran)',
        'Lenovo ThinkPad L14 Gen 1 (Core i7، 16GB، 512GB، شاشة لمس)',
        'lenovo-thinkpad-l14-gen-1-14-touchscreen-business-laptop-core-i7-16gb-ram-512gb-ssd',
        'lenovo-thinkpad-l14-gen-1-core-i7-16gb-512gb-sensor-ekran',
        'lenovo-thinkpad-l14-gen-1-core-i7-16gb-512gb-shasha-lms',
        'The Lenovo ThinkPad L14 Gen 1 is a 14-inch business laptop with a sturdy ThinkPad build and a comfortable keyboard for long working days. This configuration has a 10th Gen Intel Core i7 processor, 16GB RAM and a 512GB SSD, together with a touchscreen, a black finish and Windows 11 Pro. The 16GB of memory keeps many applications and browser tabs running smoothly at once, the 512GB SSD gives ample space for documents and software, and the touchscreen adds a hands-on way to navigate.',
        'Lenovo ThinkPad L14 Gen 1 möhkəm ThinkPad korpusu və uzun iş günləri üçün rahat klaviaturası olan 14 düymlük biznes noutbukudur. Bu konfiqurasiyada 10-cu nəsil Intel Core i7 prosessoru, 16GB RAM və 512GB SSD, həmçinin sensor ekran, qara rəngli korpus və Windows 11 Pro təqdim olunur. 16GB yaddaş çoxlu proqram və brauzer pəncərəsinin eyni vaxtda rəvan işləməsini təmin edir, 512GB SSD sənədlər və proqram təminatı üçün geniş yer ayırır, sensor ekran isə toxunuşla idarəetmə imkanı verir.',
        'يُعد Lenovo ThinkPad L14 Gen 1 حاسوبًا محمولًا للأعمال بشاشة مقاس 14 بوصة، بهيكل ThinkPad المتين ولوحة مفاتيح مريحة لأيام العمل الطويلة. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل العاشر وذاكرة 16GB RAM ووحدة تخزين 512GB SSD، إلى جانب شاشة لمس ولون أسود ونظام Windows 11 Pro. وتتيح ذاكرة 16GB تشغيل العديد من التطبيقات وعلامات تبويب المتصفح بسلاسة في الوقت نفسه، وتوفر وحدة 512GB SSD مساحة واسعة للمستندات والبرامج، فيما تضيف شاشة اللمس طريقة مباشرة للتنقل.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Lenovo L14 Gen 1 | Intel Core i7 | 10th Gen | 16GB RAM | 512GB SSD | Touch | Black | Windows 11pro
Sheet price: 1299

Check before publishing:
- 14" from the model''s specs.
- Four L14 Gen 1 units in this batch, so the AZ/AR titles carry the configuration to stay unique.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['display', '14"', NULL], ['touchable_screen', 'Yes', NULL], ['color', 'Black', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX0736 · Dell Latitude 7420 — 14" Premium Business Laptop with 11th Gen Intel Core i5
    PERFORM pg_temp.bl_add(
        'DTAX0736', 'laptop', 'Dell',
        'Dell Latitude 7420 — 14" Premium Business Laptop with 11th Gen Intel Core i5',
        'Dell Latitude 7420',
        'Dell Latitude 7420',
        'dell-latitude-7420-14-premium-business-laptop-with-11th-gen-intel-core-i5',
        'dell-latitude-7420',
        'dell-latitude-7420',
        'The Dell Latitude 7420 is a premium 14-inch business laptop from Dell''s 7000 series, with a slim, lightweight design made for professionals who work between the office, home and travel. This configuration has an 11th Gen Intel Core i5 processor with Intel Iris Xe graphics, 16GB RAM and a 512GB SSD, running Windows 11 Pro. It runs office suites, video meetings and several applications side by side smoothly, and the 512GB SSD gives ample room for documents and software.',
        'Dell Latitude 7420 Dell-in 7000 seriyasından olan, ofis, ev və səfərlər arasında işləyən mütəxəssislər üçün nazik və yüngül dizaynla hazırlanmış 14 düymlük premium biznes noutbukudur. Bu konfiqurasiyada Intel Iris Xe qrafikalı 11-ci nəsil Intel Core i5 prosessoru, 16GB RAM və 512GB SSD, həmçinin Windows 11 Pro təqdim olunur. Ofis paketləri, video görüşlər və bir neçə proqramla paralel iş rəvan gedir, 512GB SSD isə sənədlər və proqram təminatı üçün geniş yer ayırır.',
        'يُعد Dell Latitude 7420 حاسوبًا محمولًا فاخرًا للأعمال بشاشة مقاس 14 بوصة من سلسلة Dell 7000، بتصميم نحيف وخفيف الوزن موجّه للمحترفين الذين يتنقلون بين المكتب والمنزل والسفر. تأتي هذه النسخة بمعالج Intel Core i5 من الجيل الحادي عشر مع رسومات Intel Iris Xe وذاكرة 16GB RAM ووحدة تخزين 512GB SSD ونظام Windows 11 Pro. ويعمل بسلاسة مع الحزم المكتبية واجتماعات الفيديو وتشغيل عدة برامج جنبًا إلى جنب، فيما توفر وحدة 512GB SSD مساحة كافية للمستندات والبرامج.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Dell 7420 | Intel Core i5 | 11th Gen | 16GB RAM | 512GB SSD | Windows 11 Pro
Sheet price: 1299

Check before publishing:
- 14" screen and Iris Xe graphics from the model''s specs.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i5', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['gpu', 'Intel Iris Xe', NULL], ['display', '14"', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX0116 · Microsoft Surface Laptop 4 — Slim Touchscreen Laptop with 11th Gen Intel Core i7
    PERFORM pg_temp.bl_add(
        'DTAX0116', 'laptop', 'Microsoft',
        'Microsoft Surface Laptop 4 — Slim Touchscreen Laptop with 11th Gen Intel Core i7',
        'Microsoft Surface Laptop 4',
        'Microsoft Surface Laptop 4',
        'microsoft-surface-laptop-4-slim-touchscreen-laptop-with-11th-gen-intel-core-i7',
        'microsoft-surface-laptop-4',
        'microsoft-surface-laptop-4',
        'The Microsoft Surface Laptop 4 is a slim, lightweight laptop with a high-resolution PixelSense touchscreen in a 3:2 aspect ratio, which shows more of a document or web page than a standard widescreen. This configuration has an 11th Gen Intel Core i7 processor with Intel Iris Xe graphics, 16GB RAM and a 512GB SSD. It is well suited to office work, study, video calls and creative apps, and the touchscreen makes scrolling, zooming and navigating quick and natural.',
        'Microsoft Surface Laptop 4 3:2 nisbətli yüksək dəqiqlikli PixelSense sensor ekrana malik nazik və yüngül noutbukdur; bu nisbət standart geniş ekrana nisbətən sənəd və ya veb səhifənin daha çox hissəsini göstərir. Bu konfiqurasiyada Intel Iris Xe qrafikalı 11-ci nəsil Intel Core i7 prosessoru, 16GB RAM və 512GB SSD təqdim olunur. Ofis işləri, təhsil, video zənglər və yaradıcı proqramlar üçün uyğundur, sensor ekran isə sürüşdürmə, yaxınlaşdırma və naviqasiyanı sürətli və təbii edir.',
        'يُعد Microsoft Surface Laptop 4 حاسوبًا محمولًا نحيفًا وخفيف الوزن بشاشة لمس PixelSense عالية الدقة بنسبة عرض إلى ارتفاع 3:2، تعرض جزءًا أكبر من المستند أو صفحة الويب مقارنةً بالشاشات العريضة التقليدية. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الحادي عشر مع رسومات Intel Iris Xe وذاكرة 16GB RAM ووحدة تخزين 512GB SSD. وهو مناسب للأعمال المكتبية والدراسة ومكالمات الفيديو والتطبيقات الإبداعية، فيما تجعل شاشة اللمس التمرير والتكبير والتنقل سريعًا وطبيعيًا.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Microsoft Surface 4 | Intel Core i7 | 11th Gen | 16GB RAM | 512GB SSD | Touch | Core i7 | 16GB RAM | 512GB SSD
Sheet price: 1899

Check before publishing:
- CHECK THE MODEL: the sheet says ''Microsoft Surface 4''. With an 11th Gen i7 this is the Surface Laptop 4 (the Surface Pro 4 is 6th Gen). If the unit is actually a Surface Pro 7+ (a tablet), fix the title.
- Screen size (13.5" or 15") and OS are not on the sheet, so no display or OS spec is set.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['gpu', 'Intel Iris Xe', NULL], ['touchable_screen', 'Yes', NULL]]::text[]);
    -- DTAX1521 · Dell Latitude 3330 2-in-1 — 13.3" Convertible Touchscreen Laptop
    PERFORM pg_temp.bl_add(
        'DTAX1521', 'laptop', 'Dell',
        'Dell Latitude 3330 2-in-1 — 13.3" Convertible Touchscreen Laptop',
        'Dell Latitude 3330 2-in-1',
        'Dell Latitude 3330 2-in-1',
        'dell-latitude-3330-2-in-1-133-convertible-touchscreen-laptop',
        'dell-latitude-3330-2-in-1',
        'dell-latitude-3330-2-in-1',
        'The Dell Latitude 3330 2-in-1 is a compact 13.3-inch convertible laptop whose touchscreen folds back 360 degrees, so it can be used as a laptop, a tablet or in tent mode. This configuration has an 11th Gen Intel Core i5 processor with Intel Iris Xe graphics, 8GB RAM and a 256GB SSD, running Windows 11 Pro. It is a practical, portable choice for students and professionals, handling office applications, online classes, browsing and video calls comfortably.',
        'Dell Latitude 3330 2-in-1 sensor ekranı 360 dərəcə arxaya çevrilən 13.3 düymlük yığcam 2-si 1-də noutbukdur: ondan noutbuk, planşet və ya çadır rejimində istifadə etmək olur. Bu konfiqurasiyada Intel Iris Xe qrafikalı 11-ci nəsil Intel Core i5 prosessoru, 8GB RAM və 256GB SSD, həmçinin Windows 11 Pro təqdim olunur. Tələbələr və mütəxəssislər üçün praktik və daşınması asan seçimdir: ofis proqramları, onlayn dərslər, internetdə gəzinti və video zənglərin öhdəsindən rahatlıqla gəlir.',
        'يُعد Dell Latitude 3330 2-in-1 حاسوبًا محمولًا مدمجًا قابلًا للتحويل بشاشة لمس مقاس 13.3 بوصة تنطوي للخلف بزاوية 360 درجة، ما يتيح استخدامه كحاسوب محمول أو كجهاز لوحي أو في وضع الخيمة. تأتي هذه النسخة بمعالج Intel Core i5 من الجيل الحادي عشر مع رسومات Intel Iris Xe وذاكرة 8GB RAM ووحدة تخزين 256GB SSD ونظام Windows 11 Pro. وهو خيار عملي وسهل الحمل للطلاب والمحترفين، إذ يتعامل بسهولة مع التطبيقات المكتبية والدروس عبر الإنترنت وتصفح الويب ومكالمات الفيديو.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Dell 3330 (2IN 1) | Intel Core i5 | 11th Gen | 8 GB RAM | 256GB SSD | Windows 11 Pro | 8GB RAM
Sheet price: 1099

Check before publishing:
- 13.3" touchscreen and Iris Xe graphics from the model''s specs (every 3330 2-in-1 has a touchscreen).
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i5', NULL], ['ram', '8', 'GB'], ['storage', '256', 'GB'], ['gpu', 'Intel Iris Xe', NULL], ['display', '13.3"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1442 · Lenovo ThinkPad X13 Yoga Gen 1 — 13.3" Convertible Touchscreen Business Laptop
    PERFORM pg_temp.bl_add(
        'DTAX1442', 'laptop', 'Lenovo',
        'Lenovo ThinkPad X13 Yoga Gen 1 — 13.3" Convertible Touchscreen Business Laptop',
        'Lenovo ThinkPad X13 Yoga Gen 1',
        'Lenovo ThinkPad X13 Yoga Gen 1',
        'lenovo-thinkpad-x13-yoga-gen-1-133-convertible-touchscreen-business-laptop',
        'lenovo-thinkpad-x13-yoga-gen-1',
        'lenovo-thinkpad-x13-yoga-gen-1',
        'The Lenovo ThinkPad X13 Yoga Gen 1 is a 13.3-inch convertible business laptop with a 360-degree hinge, so it moves easily between laptop, tablet and tent modes. This configuration has a 10th Gen Intel Core i7 processor, 16GB RAM and a 256GB SSD, running Windows 11 Pro. It combines ThinkPad durability and a comfortable keyboard with a touchscreen for reading, reviewing and presenting, and 16GB of memory keeps multitasking smooth.',
        'Lenovo ThinkPad X13 Yoga Gen 1 360 dərəcə fırlanan menteşəyə malik 13.3 düymlük 2-si 1-də biznes noutbukudur: noutbuk, planşet və çadır rejimləri arasında asanlıqla keçid edir. Bu konfiqurasiyada 10-cu nəsil Intel Core i7 prosessoru, 16GB RAM və 256GB SSD, həmçinin Windows 11 Pro təqdim olunur. ThinkPad möhkəmliyini və rahat klaviaturasını oxumaq, nəzərdən keçirmək və təqdimat etmək üçün sensor ekranla birləşdirir, 16GB yaddaş isə çoxsaylı tapşırıqlarla işi rəvan saxlayır.',
        'يُعد Lenovo ThinkPad X13 Yoga Gen 1 حاسوبًا محمولًا للأعمال قابلًا للتحويل بشاشة مقاس 13.3 بوصة ومفصل يدور بزاوية 360 درجة، ما يتيح التنقل بسهولة بين أوضاع الحاسوب المحمول والجهاز اللوحي والخيمة. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل العاشر وذاكرة 16GB RAM ووحدة تخزين 256GB SSD ونظام Windows 11 Pro. ويجمع بين متانة ThinkPad ولوحة مفاتيحها المريحة وشاشة لمس للقراءة والمراجعة وتقديم العروض، فيما تحافظ ذاكرة 16GB على سلاسة تعدد المهام.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: LENOVO X13 YOGA | 2 in 1 | Intel Core i7 | 10th Gen | 16GB RAM | 256 GB SSD | Windows 11 Pro | 256GB SSD
Sheet price: 1399

Check before publishing:
- Sheet says ''X13 YOGA'' with a 10th Gen CPU, which is the Gen 1. 13.3" touchscreen from the model''s specs.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '256', 'GB'], ['display', '13.3"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1518 · Dell Precision 3480 — 14" Touchscreen Mobile Workstation with 4GB Graphics
    PERFORM pg_temp.bl_add(
        'DTAX1518', 'laptop', 'Dell',
        'Dell Precision 3480 — 14" Touchscreen Mobile Workstation with 4GB Graphics',
        'Dell Precision 3480',
        'Dell Precision 3480',
        'dell-precision-3480-14-touchscreen-mobile-workstation-with-4gb-graphics',
        'dell-precision-3480',
        'dell-precision-3480',
        'The Dell Precision 3480 is a 14-inch mobile workstation that packs professional performance into a compact, easy-to-carry chassis. This configuration has a 13th Gen Intel Core i7 processor, 4GB of dedicated graphics, 16GB RAM and a 512GB SSD, with a touchscreen and Windows 11 Pro. The dedicated graphics handle CAD, 2D and 3D design and photo and video work, while the compact 14-inch size makes it practical to take to sites, clients and meetings.',
        'Dell Precision 3480 peşəkar performansı yığcam və daşınması asan korpusda təqdim edən 14 düymlük mobil iş stansiyasıdır. Bu konfiqurasiyada 13-cü nəsil Intel Core i7 prosessoru, 4GB həcmli diskret qrafika kartı, 16GB RAM və 512GB SSD, həmçinin sensor ekran və Windows 11 Pro təqdim olunur. Diskret qrafika CAD, 2D və 3D dizayn, foto və video işlərinin öhdəsindən gəlir, yığcam 14 düymlük ölçü isə onu obyektlərə, müştəri görüşlərinə və iclaslara aparmağı asanlaşdırır.',
        'يُعد Dell Precision 3480 محطة عمل محمولة بشاشة مقاس 14 بوصة، تجمع الأداء الاحترافي في هيكل مدمج وسهل الحمل. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الثالث عشر مع بطاقة رسومات مخصصة بسعة 4GB وذاكرة 16GB RAM ووحدة تخزين 512GB SSD، إلى جانب شاشة لمس ونظام Windows 11 Pro. وتتعامل بطاقة الرسومات المخصصة مع برامج CAD والتصميم ثنائي وثلاثي الأبعاد وأعمال الصور والفيديو، بينما يجعل حجمه المدمج مقاس 14 بوصة من السهل اصطحابه إلى المواقع واجتماعات العملاء.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: DELL PRECISION 3480 | Intel Core i7 | 13th Gen | 16GB RAM | 512 GB SSD | 4 GB GPU | TOUCH | Windows 11 pro | 512GB SSD
Sheet price: 2299

Check before publishing:
- 14" from the model''s specs. GPU vendor not named in the copy.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['gpu', '4', 'GB'], ['display', '14"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1477 · Lenovo ThinkPad P53s — 15.6" Slim Mobile Workstation with 2GB Graphics
    PERFORM pg_temp.bl_add(
        'DTAX1477', 'laptop', 'Lenovo',
        'Lenovo ThinkPad P53s — 15.6" Slim Mobile Workstation with 2GB Graphics',
        'Lenovo ThinkPad P53s',
        'Lenovo ThinkPad P53s',
        'lenovo-thinkpad-p53s-156-slim-mobile-workstation-with-2gb-graphics',
        'lenovo-thinkpad-p53s',
        'lenovo-thinkpad-p53s',
        'The Lenovo ThinkPad P53s is a slim 15.6-inch mobile workstation that combines professional graphics with the portability of a regular business laptop. This configuration has an 8th Gen Intel Core i7 processor, 2GB of dedicated NVIDIA Quadro graphics, 16GB RAM and a 512GB SSD, running Windows 11 Pro. It suits engineering and design software, 2D CAD and photo editing as well as everyday office work, and the 15.6-inch screen gives comfortable room for spreadsheets and drawings.',
        'Lenovo ThinkPad P53s peşəkar qrafikanı adi biznes noutbukunun daşınma rahatlığı ilə birləşdirən nazik 15.6 düymlük mobil iş stansiyasıdır. Bu konfiqurasiyada 8-ci nəsil Intel Core i7 prosessoru, 2GB həcmli diskret NVIDIA Quadro qrafika kartı, 16GB RAM və 512GB SSD, həmçinin Windows 11 Pro təqdim olunur. Mühəndislik və dizayn proqramları, 2D CAD və foto redaktəsi, eləcə də gündəlik ofis işləri üçün uyğundur, 15.6 düymlük ekran isə cədvəllər və çertyojlar üçün rahat sahə təqdim edir.',
        'يُعد Lenovo ThinkPad P53s محطة عمل محمولة نحيفة بشاشة مقاس 15.6 بوصة، تجمع بين الرسومات الاحترافية وسهولة حمل حاسوب الأعمال العادي. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الثامن مع بطاقة رسومات NVIDIA Quadro مخصصة بسعة 2GB وذاكرة 16GB RAM ووحدة تخزين 512GB SSD ونظام Windows 11 Pro. وهو مناسب لبرامج الهندسة والتصميم وبرامج CAD ثنائية الأبعاد وتحرير الصور، إلى جانب الأعمال المكتبية اليومية، فيما توفر الشاشة مقاس 15.6 بوصة مساحة مريحة لجداول البيانات والرسومات.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Lenovo P53S | Intel Core i7 | 8th Gen | 16GB RAM | 512GB SSD | 2 GB GPU | Windows 11 pro
Sheet price: 1599

Check before publishing:
- 15.6" screen from the model''s specs; the P53s''s only dedicated GPU is the 2GB NVIDIA Quadro P520.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['gpu', '2', 'GB'], ['display', '15.6"', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1438 · Dell Latitude 5500 — 15.6" Business Laptop with Intel Core i7
    PERFORM pg_temp.bl_add(
        'DTAX1438', 'laptop', 'Dell',
        'Dell Latitude 5500 — 15.6" Business Laptop with Intel Core i7',
        'Dell Latitude 5500',
        'Dell Latitude 5500',
        'dell-latitude-5500-156-business-laptop-with-intel-core-i7',
        'dell-latitude-5500',
        'dell-latitude-5500',
        'The Dell Latitude 5500 is a 15.6-inch business laptop with a large screen and a full-size keyboard with a numeric keypad, well suited to spreadsheets, accounting and data entry. This configuration has an 8th Gen Intel Core i7 processor, 8GB RAM and a 256GB SSD, running Windows 11 Pro. It handles office applications, web browsing and video calls reliably, and the SSD keeps start-up and file access quick.',
        'Dell Latitude 5500 böyük ekranı və rəqəm bloku olan tam ölçülü klaviaturası ilə cədvəllər, mühasibatlıq və məlumat daxil etmək üçün uyğun olan 15.6 düymlük biznes noutbukudur. Bu konfiqurasiyada 8-ci nəsil Intel Core i7 prosessoru, 8GB RAM və 256GB SSD, həmçinin Windows 11 Pro təqdim olunur. Ofis proqramları, internetdə gəzinti və video zənglərin öhdəsindən etibarlı şəkildə gəlir, SSD isə sistemin açılmasını və fayllara girişi sürətli edir.',
        'يُعد Dell Latitude 5500 حاسوبًا محمولًا للأعمال بشاشة كبيرة مقاس 15.6 بوصة ولوحة مفاتيح كاملة الحجم مع لوحة أرقام، ما يجعله مناسبًا لجداول البيانات والمحاسبة وإدخال البيانات. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الثامن وذاكرة 8GB RAM ووحدة تخزين 256GB SSD ونظام Windows 11 Pro. ويتعامل بكفاءة مع التطبيقات المكتبية وتصفح الإنترنت ومكالمات الفيديو، فيما تحافظ وحدة SSD على سرعة التشغيل والوصول إلى الملفات.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Dell 5500 Intel Core i7 | 8th Gen | 8GB RAM | 256GB SSD | Windows 11 Pro
Sheet price: 899

Check before publishing:
- 15.6" screen and numeric keypad from the model''s specs.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '8', 'GB'], ['storage', '256', 'GB'], ['display', '15.6"', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1535 · Lenovo ThinkPad P15v Gen 3 — 15.6" Mobile Workstation, 16GB RAM, 512GB SSD
    PERFORM pg_temp.bl_add(
        'DTAX1535', 'laptop', 'Lenovo',
        'Lenovo ThinkPad P15v Gen 3 — 15.6" Mobile Workstation, 16GB RAM, 512GB SSD',
        'Lenovo ThinkPad P15v Gen 3 (16GB, 512GB)',
        'Lenovo ThinkPad P15v Gen 3 (16GB، 512GB)',
        'lenovo-thinkpad-p15v-gen-3-156-mobile-workstation-16gb-ram-512gb-ssd',
        'lenovo-thinkpad-p15v-gen-3-16gb-512gb',
        'lenovo-thinkpad-p15v-gen-3-16gb-512gb',
        'The Lenovo ThinkPad P15v Gen 3 is a 15.6-inch mobile workstation that brings professional graphics to engineers, architects and designers in a ThinkPad body. This configuration has a 12th Gen Intel Core i7 processor, 4GB of dedicated NVIDIA graphics, 16GB RAM and a 512GB SSD, running Windows 11 Pro. The dedicated graphics speed up CAD, 3D modelling, rendering and video editing, and the 512GB SSD gives room for large project files and software.',
        'Lenovo ThinkPad P15v Gen 3 mühəndislər, memarlar və dizaynerlər üçün peşəkar qrafikanı ThinkPad korpusunda təqdim edən 15.6 düymlük mobil iş stansiyasıdır. Bu konfiqurasiyada 12-ci nəsil Intel Core i7 prosessoru, 4GB həcmli diskret NVIDIA qrafika kartı, 16GB RAM və 512GB SSD, həmçinin Windows 11 Pro təqdim olunur. Diskret qrafika CAD, 3D modelləşdirmə, render və video montaj işlərini sürətləndirir, 512GB SSD isə böyük layihə faylları və proqram təminatı üçün yer ayırır.',
        'يُعد Lenovo ThinkPad P15v Gen 3 محطة عمل محمولة بشاشة مقاس 15.6 بوصة، تقدم رسومات احترافية للمهندسين والمعماريين والمصممين في هيكل ThinkPad. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الثاني عشر مع بطاقة رسومات NVIDIA مخصصة بسعة 4GB وذاكرة 16GB RAM ووحدة تخزين 512GB SSD ونظام Windows 11 Pro. وتعمل بطاقة الرسومات المخصصة على تسريع برامج CAD والنمذجة ثلاثية الأبعاد والتصيير وتحرير الفيديو، فيما توفر وحدة 512GB SSD مساحة لملفات المشاريع الكبيرة والبرامج.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: Lenovo P15V | Intel Core i7 | 12th Gen | 16GB RAM | 512 GB SSD | 4GB GPU | Windows 11 Pro | 512GB SSD
Sheet price: 2599

Check before publishing:
- Sheet says ''P15V'' with a 12th Gen CPU, which is the ThinkPad P15v Gen 3. 15.6" screen and NVIDIA graphics from the model''s specs.
- Two P15v Gen 3 units in this batch, so the AZ/AR titles carry RAM/SSD to stay unique.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['gpu', '4', 'GB'], ['display', '15.6"', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1473 · HP EliteBook 865 — 16" Business Laptop with AMD Ryzen 7
    PERFORM pg_temp.bl_add(
        'DTAX1473', 'laptop', 'HP',
        'HP EliteBook 865 — 16" Business Laptop with AMD Ryzen 7',
        'HP EliteBook 865',
        'HP EliteBook 865',
        'hp-elitebook-865-16-business-laptop-with-amd-ryzen-7',
        'hp-elitebook-865',
        'hp-elitebook-865',
        'The HP EliteBook 865 is a 16-inch premium business laptop that gives you a large, comfortable screen in a slim aluminium design. This configuration has an AMD Ryzen 7 processor, 16GB RAM and a 512GB SSD, running Windows 11 Pro. The Ryzen 7 processor delivers strong multi-core performance for office work, multitasking and video conferencing, and the 16-inch display leaves plenty of room to work with documents and applications side by side.',
        'HP EliteBook 865 nazik alüminium dizaynda böyük və rahat ekran təqdim edən 16 düymlük premium biznes noutbukudur. Bu konfiqurasiyada AMD Ryzen 7 prosessoru, 16GB RAM və 512GB SSD, həmçinin Windows 11 Pro təqdim olunur. Ryzen 7 prosessoru ofis işləri, çoxsaylı tapşırıqlar və video konfranslar üçün güclü çoxnüvəli performans təmin edir, 16 düymlük ekran isə sənəd və proqramlarla yan-yana işləmək üçün geniş yer ayırır.',
        'يُعد HP EliteBook 865 حاسوبًا محمولًا فاخرًا للأعمال بشاشة مقاس 16 بوصة، يوفر شاشة كبيرة ومريحة في تصميم نحيف من الألومنيوم. تأتي هذه النسخة بمعالج AMD Ryzen 7 وذاكرة 16GB RAM ووحدة تخزين 512GB SSD ونظام Windows 11 Pro. ويقدم معالج Ryzen 7 أداءً قويًا متعدد الأنوية للأعمال المكتبية وتعدد المهام ومؤتمرات الفيديو، فيما توفر الشاشة مقاس 16 بوصة مساحة واسعة للعمل على المستندات والتطبيقات جنبًا إلى جنب.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: HP elitebook  865 | AMD RYZEN 7 | 16 GB RAM | 512 GB SSD | Windows 11 pro | 16GB RAM | 512GB SSD
Sheet price: 1999

Check before publishing:
- CHECK THE GENERATION: the sheet does not say G9, G10 or G11, so the title has none. Add it before publishing. 16" is the same on every EliteBook 865 generation.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'AMD Ryzen 7', NULL], ['ram', '16', 'GB'], ['storage', '512', 'GB'], ['display', '16"', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
    -- DTAX1557 · HP EliteBook 850 G5 — 15.6" Business Laptop with 2GB Dedicated Graphics
    PERFORM pg_temp.bl_add(
        'DTAX1557', 'laptop', 'HP',
        'HP EliteBook 850 G5 — 15.6" Business Laptop with 2GB Dedicated Graphics',
        'HP EliteBook 850 G5',
        'HP EliteBook 850 G5',
        'hp-elitebook-850-g5-156-business-laptop-with-2gb-dedicated-graphics',
        'hp-elitebook-850-g5',
        'hp-elitebook-850-g5',
        'The HP EliteBook 850 G5 is a 15.6-inch business laptop with a slim aluminium design and a large screen for comfortable all-day work. This configuration has an 8th Gen Intel Core i7 processor, 2GB of dedicated AMD Radeon graphics, 8GB RAM and a 256GB SSD, running Windows 11 Pro. It handles office applications, browsing and video calls with ease, and the dedicated graphics give extra headroom for photo editing and light design work.',
        'HP EliteBook 850 G5 nazik alüminium dizayna və bütün gün rahat iş üçün böyük ekrana malik 15.6 düymlük biznes noutbukudur. Bu konfiqurasiyada 8-ci nəsil Intel Core i7 prosessoru, 2GB həcmli diskret AMD Radeon qrafika kartı, 8GB RAM və 256GB SSD, həmçinin Windows 11 Pro təqdim olunur. Ofis proqramları, internetdə gəzinti və video zənglərin öhdəsindən asanlıqla gəlir, diskret qrafika isə foto redaktəsi və yüngül dizayn işləri üçün əlavə imkan yaradır.',
        'يُعد HP EliteBook 850 G5 حاسوبًا محمولًا للأعمال بشاشة مقاس 15.6 بوصة، بتصميم نحيف من الألومنيوم وشاشة كبيرة للعمل المريح طوال اليوم. تأتي هذه النسخة بمعالج Intel Core i7 من الجيل الثامن مع بطاقة رسومات AMD Radeon مخصصة بسعة 2GB وذاكرة 8GB RAM ووحدة تخزين 256GB SSD ونظام Windows 11 Pro. ويتعامل بسهولة مع التطبيقات المكتبية وتصفح الإنترنت ومكالمات الفيديو، فيما توفر بطاقة الرسومات المخصصة قدرة إضافية لتحرير الصور وأعمال التصميم البسيطة.',
        'Added by SQL batch 2026-09-29. No price, no images and no store listing yet.
Original sheet line: HP 850 G5 | Intel Core i7 | 8th Gen | 8GB RAM | 256 GB SSD | 2 GB GPU | Windows 11 pro | 256GB SSD
Sheet price: 1199

Check before publishing:
- Sheet says ''HP 850 G5'', which is the EliteBook 850 G5. 15.6" screen from the model''s specs; its only dedicated GPU is the 2GB AMD Radeon RX 540.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i7', NULL], ['ram', '8', 'GB'], ['storage', '256', 'GB'], ['gpu', '2', 'GB'], ['display', '15.6"', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
END $$;


-- -----------------------------------------------------------------------------------------------------
-- 4. Summary, then commit. Change COMMIT to ROLLBACK for a dry run.
-- -----------------------------------------------------------------------------------------------------
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN SELECT outcome, count(*) AS n FROM bl_report WHERE sku NOT LIKE '(%' GROUP BY outcome LOOP
        RAISE NOTICE '% product(s) %', r.n, r.outcome;
    END LOOP;
END $$;

COMMIT;

SELECT sku, outcome, detail, title FROM bl_report ORDER BY seq;
