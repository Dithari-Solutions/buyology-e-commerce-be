-- Editable registry of product spec codes (previously a hardcoded frontend constant).
-- Created here for prod (ddl-auto=validate); on dev (ddl-auto=update) Hibernate also
-- creates it from the entity. CREATE/INSERT are idempotent so re-runs are safe.

CREATE TABLE IF NOT EXISTS spec_codes (
    id            UUID PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,
    label_en      VARCHAR(100),
    label_az      VARCHAR(100),
    label_ar      VARCHAR(100),
    filterable    BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_spec_codes_code UNIQUE (code)
);

-- Seed the 8 codes the dashboard previously hardcoded. The 7 backend-filterable
-- ones are marked filterable=true; gpu is display-only (no storefront filter yet).
INSERT INTO spec_codes (id, code, label_en, label_az, label_ar, filterable, display_order)
VALUES
    (gen_random_uuid(), 'ram',               'RAM',               'RAM',                 'ذاكرة الوصول العشوائي', TRUE,  0),
    (gen_random_uuid(), 'storage',           'Storage',           'Yaddaş',              'التخزين',               TRUE,  1),
    (gen_random_uuid(), 'processor',         'Processor',         'Prosessor',           'المعالج',               TRUE,  2),
    (gen_random_uuid(), 'gpu',               'GPU',               'Qrafika prosessoru',  'معالج الرسومات',        FALSE, 3),
    (gen_random_uuid(), 'screen_size',       'Screen Size',       'Ekran ölçüsü',        'حجم الشاشة',            TRUE,  4),
    (gen_random_uuid(), 'touchable_screen',  'Touchable Screen',  'Sensorlu ekran',      'شاشة تعمل باللمس',      TRUE,  5),
    (gen_random_uuid(), 'operating_system',  'Operating System',  'Əməliyyat sistemi',   'نظام التشغيل',          TRUE,  6),
    (gen_random_uuid(), 'keyboard_language', 'Keyboard Language', 'Klaviatura dili',     'لغة لوحة المفاتيح',     TRUE,  7)
ON CONFLICT (code) DO NOTHING;
