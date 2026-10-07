-- =====================================================================================================
-- Buyology catalogue: add 4 tablets from the 2026-09-29 supplier sheet as DRAFTS
--   -> category "iPads & Tablets" (the one created in the dashboard; this script does not create it)
--
-- Companion to 2026-09-29_add_laptops_and_imacs.sql and built the same way. Either file can run first.
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
-- Left out on purpose: DTAX1274 (Microsoft Surface Go) is already live, in the Laptop category.
--
-- HOW TO RUN (PostgreSQL 15, against the production database):
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f 2026-09-29_add_tablets.sql
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
-- UNDO (only while they are still drafts; touches this file's products only):
--   DELETE FROM product_spec_options  WHERE group_id IN (SELECT g.id FROM product_spec_groups g JOIN products p ON p.id = g.product_id WHERE p.import_notes LIKE 'Added by SQL batch 2026-09-29-tablets.%' AND p.status = 'DRAFT');
--   DELETE FROM product_spec_groups   WHERE product_id IN (SELECT id FROM products WHERE import_notes LIKE 'Added by SQL batch 2026-09-29-tablets.%' AND status = 'DRAFT');
--   DELETE FROM product_translations  WHERE product_id IN (SELECT id FROM products WHERE import_notes LIKE 'Added by SQL batch 2026-09-29-tablets.%' AND status = 'DRAFT');
--   DELETE FROM products              WHERE import_notes LIKE 'Added by SQL batch 2026-09-29-tablets.%' AND status = 'DRAFT';
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
                            'global_spec_options', 'global_spec_option_translations');
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
-- 2. Category: "iPads & Tablets", created in the dashboard. Found by its id, or else by its English slug.
-- -----------------------------------------------------------------------------------------------------
DO $$
DECLARE
    v_tablets uuid;
BEGIN
    SELECT c.id INTO v_tablets
      FROM product_categories c
      JOIN product_category_translations t ON t.category_id = c.id AND upper(t.language) = 'EN'
     WHERE c.id = '579320dc-af96-4352-b9d2-c9c2ab735b22' OR t.slug = 'ipads-and-tablets'
     ORDER BY (c.id = '579320dc-af96-4352-b9d2-c9c2ab735b22') DESC
     LIMIT 1;
    IF v_tablets IS NULL THEN
        RAISE EXCEPTION 'The "iPads & Tablets" category was not found. Nothing has been written.';
    END IF;

    INSERT INTO bl_category (key, id) VALUES ('tablets', v_tablets);
    INSERT INTO bl_report (sku, outcome, detail) VALUES ('(category)', 'FOUND', 'iPads & Tablets ' || v_tablets);
END $$;


-- -----------------------------------------------------------------------------------------------------
-- 3. The products.
--    Arguments: sku, category, brand, title EN/AZ/AR, slug EN/AZ/AR (built exactly as SlugUtils.toSlug
--    builds them), description EN/AZ/AR, import notes, specs as [code, value, unit].
-- -----------------------------------------------------------------------------------------------------
DO $$
BEGIN

    -- DTA1167 · Apple iPad 11 (A16) — 11" Liquid Retina Tablet, Wi-Fi, 128GB
    PERFORM pg_temp.bl_add(
        'DTA1167', 'tablets', 'Apple',
        'Apple iPad 11 (A16) — 11" Liquid Retina Tablet, Wi-Fi, 128GB',
        'Apple iPad 11 (A16)',
        'Apple iPad 11 (A16)',
        'apple-ipad-11-a16-11-liquid-retina-tablet-wi-fi-128gb',
        'apple-ipad-11-a16',
        'apple-ipad-11-a16',
        'The Apple iPad 11 (A16) is Apple''s everyday iPad, with an 11-inch Liquid Retina display (2360 × 1640) that is bright and sharp for reading, streaming and note-taking. This model has the A16 chip, 128GB of storage and Wi-Fi 6, running iPadOS. Touch ID in the top button unlocks it and approves Apple Pay, the USB-C port charges it and connects accessories, and it works with Apple Pencil (USB-C) and Apple Pencil (1st generation), sold separately. It is a good fit for study, work on the go, entertainment and family use.',
        'Apple iPad 11 (A16) Apple-ın gündəlik istifadə üçün iPad modelidir: 11 düymlük Liquid Retina (2360 × 1640) ekranı oxumaq, video izləmək və qeydlər aparmaq üçün parlaq və aydın təsvir verir. Bu modeldə A16 çipi, 128GB yaddaş və Wi-Fi 6, həmçinin iPadOS əməliyyat sistemi təqdim olunur. Yuxarı düymədəki Touch ID cihazın kilidini açır və Apple Pay ödənişlərini təsdiqləyir, USB-C portu isə şarj və aksesuarların qoşulması üçündür; cihaz ayrıca satılan Apple Pencil (USB-C) və Apple Pencil (1-ci nəsil) ilə işləyir. Təhsil, yolda iş, əyləncə və ailə istifadəsi üçün uyğun seçimdir.',
        'يُعد Apple iPad 11 (A16) جهاز iPad المخصص للاستخدام اليومي من Apple، بشاشة Liquid Retina مقاس 11 بوصة (2360 × 1640) ساطعة وواضحة للقراءة ومشاهدة الفيديو وتدوين الملاحظات. يأتي هذا الطراز بشريحة A16 وسعة تخزين 128GB وتقنية Wi-Fi 6 ونظام iPadOS. ويتيح Touch ID المدمج في الزر العلوي فتح الجهاز وتأكيد مدفوعات Apple Pay، بينما يُستخدم منفذ USB-C للشحن وتوصيل الملحقات، كما يعمل الجهاز مع Apple Pencil (USB-C) وApple Pencil (الجيل الأول) اللذين يُباعان بشكل منفصل. وهو خيار مناسب للدراسة والعمل أثناء التنقل والترفيه والاستخدام العائلي.',
        'Added by SQL batch 2026-09-29-tablets. No price, no images and no store listing yet.
Original sheet line: IPAD 11 | A16 | 128GB | WIFI | 128GB SSD
Sheet price: 1599

Check before publishing:
- CHECK CONDITION: this is Apple''s current iPad (released 2025). If the unit is new, change the condition from Refurbished to New.
- Sheet says ''IPAD 11 | A16'', which is the iPad 11-inch (A16). The 11" Liquid Retina screen, Touch ID, USB-C and Apple Pencil support come from Apple''s specs. Colour is not on the sheet.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'A16', NULL], ['storage', '128', 'GB'], ['display', '11"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'iPadOS', NULL], ['connectivity', 'Wi-Fi', NULL]]::text[]);
    -- DTAX1558 · Apple iPad 8 (2020) — 10.2" Retina Tablet, 32GB
    PERFORM pg_temp.bl_add(
        'DTAX1558', 'tablets', 'Apple',
        'Apple iPad 8 (2020) — 10.2" Retina Tablet, 32GB',
        'Apple iPad 8 (2020)',
        'Apple iPad 8 (2020)',
        'apple-ipad-8-2020-102-retina-tablet-32gb',
        'apple-ipad-8-2020',
        'apple-ipad-8-2020',
        'The Apple iPad 8 (2020), the 8th-generation iPad, has a 10.2-inch Retina display and Apple''s A12 Bionic chip, making it a dependable tablet for browsing, video calls, streaming and schoolwork. This unit has 32GB of storage and runs iPadOS. The Home button with Touch ID unlocks it securely, and it works with Apple Pencil (1st generation) and Apple''s Smart Keyboard, both sold separately. It is an affordable choice for students, children and anyone who wants a simple, reliable iPad.',
        'Apple iPad 8 (2020), yəni 8-ci nəsil iPad, 10.2 düymlük Retina ekrana və Apple-ın A12 Bionic çipinə malikdir; bu da onu internetdə gəzinti, video zənglər, video izləmə və dərslər üçün etibarlı planşetə çevirir. Bu cihazda 32GB yaddaş var və o, iPadOS ilə işləyir. Touch ID-li Home düyməsi cihazın kilidini təhlükəsiz şəkildə açır, cihaz ayrıca satılan Apple Pencil (1-ci nəsil) və Apple Smart Keyboard ilə işləyir. Tələbələr, uşaqlar və sadə, etibarlı iPad istəyən hər kəs üçün sərfəli seçimdir.',
        'يُعد Apple iPad 8 (2020)، أي الجيل الثامن من iPad، جهازًا لوحيًا موثوقًا بشاشة Retina مقاس 10.2 بوصة وشريحة A12 Bionic من Apple، ما يجعله مناسبًا لتصفح الإنترنت ومكالمات الفيديو ومشاهدة المحتوى والواجبات المدرسية. تأتي هذه الوحدة بسعة تخزين 32GB وتعمل بنظام iPadOS. ويتيح زر الشاشة الرئيسية المزود بـ Touch ID فتح الجهاز بأمان، كما يعمل مع Apple Pencil (الجيل الأول) ولوحة مفاتيح Smart Keyboard من Apple اللذين يُباعان بشكل منفصل. وهو خيار اقتصادي للطلاب والأطفال ولكل من يريد جهاز iPad بسيطًا وموثوقًا.',
        'Added by SQL batch 2026-09-29-tablets. No price, no images and no store listing yet.
Original sheet line: I PAD 8 32GB
Sheet price: 399

Check before publishing:
- Sheet says ''I PAD 8'', which is the 8th-generation iPad (2020). The 10.2" Retina screen, A12 Bionic chip, Touch ID and Apple Pencil (1st generation) support come from Apple''s specs.
- Wi-Fi or Wi-Fi + Cellular, and the colour, are not on the sheet.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'A12 Bionic', NULL], ['storage', '32', 'GB'], ['display', '10.2"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'iPadOS', NULL]]::text[]);
    -- DTAX1559 · Samsung Galaxy Tab Active2 — 8" Rugged IP68 Android Tablet, 3GB RAM
    PERFORM pg_temp.bl_add(
        'DTAX1559', 'tablets', 'Samsung',
        'Samsung Galaxy Tab Active2 — 8" Rugged IP68 Android Tablet, 3GB RAM',
        'Samsung Galaxy Tab Active2',
        'Samsung Galaxy Tab Active2',
        'samsung-galaxy-tab-active2-8-rugged-ip68-android-tablet-3gb-ram',
        'samsung-galaxy-tab-active2',
        'samsung-galaxy-tab-active2',
        'The Samsung Galaxy Tab Active2 is a rugged 8-inch Android tablet built for work in the field, in warehouses and on job sites. It is IP68 water- and dust-resistant and meets the MIL-STD-810G military standard for drops, vibration and temperature, and its touchscreen supports the S Pen and can be used with gloves. This unit has 3GB RAM, and storage can be expanded with a microSD card. It suits logistics, inspections, deliveries and other jobs where an ordinary tablet would not survive.',
        'Samsung Galaxy Tab Active2 sahə işləri, anbarlar və tikinti obyektləri üçün hazırlanmış 8 düymlük möhkəm Android planşetidir. IP68 standartına uyğun olaraq suya və toza davamlıdır, düşmə, vibrasiya və temperatur üzrə MIL-STD-810G hərbi standartına cavab verir, sensor ekranı isə S Pen-i dəstəkləyir və əlcəklə də istifadə oluna bilir. Bu cihazda 3GB RAM var, yaddaşı isə microSD kartla artırmaq mümkündür. Logistika, yoxlamalar, çatdırılma və adi planşetin tab gətirməyəcəyi digər işlər üçün uyğundur.',
        'يُعد Samsung Galaxy Tab Active2 جهازًا لوحيًا متينًا يعمل بنظام Android بشاشة مقاس 8 بوصات، مصممًا للعمل الميداني والمستودعات ومواقع العمل. وهو مقاوم للماء والغبار وفق معيار IP68 ويلبي المعيار العسكري MIL-STD-810G لمقاومة السقوط والاهتزاز ودرجات الحرارة، كما تدعم شاشته اللمسية قلم S Pen ويمكن استخدامها بالقفازات. تأتي هذه الوحدة بذاكرة 3GB RAM، ويمكن توسيع مساحة التخزين ببطاقة microSD. وهو مناسب لأعمال الخدمات اللوجستية والتفتيش والتوصيل وغيرها من الأعمال التي لا يتحملها الجهاز اللوحي العادي.',
        'Added by SQL batch 2026-09-29-tablets. No price, no images and no store listing yet.
Original sheet line: SamsungGalaxy Tab Active 2 | 3GB | 64 GB
Sheet price: 499

Check before publishing:
- CHECK STORAGE: the sheet says 64GB, but Samsung only made the Tab Active2 with 16GB (and 3GB RAM). 64GB storage with 4GB RAM is the Tab Active3. Either this unit has a 64GB microSD card or it is a different model. Check it, then add the storage spec and put the storage in the copy. No storage is claimed until then.
- 8" screen, IP68, MIL-STD-810G and S Pen support come from Samsung''s specs. The copy says the screen supports the S Pen, not that one is included. Wi-Fi or LTE is not on the sheet.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['ram', '3', 'GB'], ['display', '8"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'Android', NULL], ['water_resistance', 'IP68', NULL]]::text[]);
    -- DTAX0325 · Microsoft Surface Pro 7 — 12.3" 2-in-1 Windows Tablet, Core i5, 8GB RAM, 128GB SSD
    PERFORM pg_temp.bl_add(
        'DTAX0325', 'tablets', 'Microsoft',
        'Microsoft Surface Pro 7 — 12.3" 2-in-1 Windows Tablet, Core i5, 8GB RAM, 128GB SSD',
        'Microsoft Surface Pro 7',
        'Microsoft Surface Pro 7',
        'microsoft-surface-pro-7-123-2-in-1-windows-tablet-core-i5-8gb-ram-128gb-ssd',
        'microsoft-surface-pro-7',
        'microsoft-surface-pro-7',
        'The Microsoft Surface Pro 7 is a 2-in-1 Windows tablet with a 12.3-inch PixelSense touchscreen (2736 × 1824) and a built-in kickstand, so it works as a tablet or, with a Type Cover keyboard, as a laptop. This configuration has a 10th Gen Intel Core i5 processor, 8GB RAM and a 128GB SSD, running Windows 11 Pro. Because it runs full Windows, it handles the same office applications and software as a laptop, and its USB-C and USB-A ports connect displays and accessories. The Type Cover and Surface Pen are sold separately.',
        'Microsoft Surface Pro 7 12.3 düymlük PixelSense sensor ekranı (2736 × 1824) və daxili dayağı olan 2-si 1-də Windows planşetidir: ondan planşet kimi, Type Cover klaviaturası ilə isə noutbuk kimi istifadə etmək olur. Bu konfiqurasiyada 10-cu nəsil Intel Core i5 prosessoru, 8GB RAM və 128GB SSD, həmçinin Windows 11 Pro təqdim olunur. Tam Windows ilə işlədiyi üçün noutbukdakı ofis proqramlarını və proqram təminatını işlədir, USB-C və USB-A portları isə monitor və aksesuarları qoşmağa imkan verir. Type Cover və Surface Pen ayrıca satılır.',
        'يُعد Microsoft Surface Pro 7 جهازًا لوحيًا بنظام Windows قابلًا للتحويل 2 في 1، بشاشة لمس PixelSense مقاس 12.3 بوصة (2736 × 1824) وحامل مدمج، ما يتيح استخدامه كجهاز لوحي أو كحاسوب محمول عند توصيل لوحة مفاتيح Type Cover. تأتي هذه النسخة بمعالج Intel Core i5 من الجيل العاشر وذاكرة 8GB RAM ووحدة تخزين 128GB SSD ونظام Windows 11 Pro. ولأنه يعمل بنظام Windows الكامل، فإنه يشغّل التطبيقات المكتبية والبرامج نفسها التي تعمل على الحاسوب المحمول، كما تتيح منافذ USB-C وUSB-A توصيل الشاشات والملحقات. تُباع لوحة المفاتيح Type Cover وقلم Surface Pen بشكل منفصل.',
        'Added by SQL batch 2026-09-29-tablets. No price, no images and no store listing yet.
Original sheet line: Microsoft Surface Pro 7 | Intel Core i5 | 10th Gen | 8GB RAM | 128 GB SSD | Windows 11 Pro | 128GB SSD
Sheet price: 999

Check before publishing:
- 12.3" PixelSense screen, kickstand and USB-C/USB-A ports come from Microsoft''s specs.
- The copy says the Type Cover keyboard and Surface Pen are sold separately. If this unit comes with a Type Cover, remove that sentence from all three descriptions.
- Condition set to Refurbished, grade A, to match the rest of the catalogue - confirm. Stock set to 1 unit.',
        ARRAY[['processor', 'Intel Core i5', NULL], ['ram', '8', 'GB'], ['storage', '128', 'GB'], ['display', '12.3"', NULL], ['touchable_screen', 'Yes', NULL], ['operating_system', 'Windows 11 Pro', NULL]]::text[]);
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
