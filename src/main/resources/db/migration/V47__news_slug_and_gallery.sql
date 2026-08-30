-- Newsroom: an SEO-usable URL and more than one picture per article.
--
-- The slug matters for the same reason product URLs got one: /news/<uuid> is unreadable, tells a
-- search engine nothing, and cannot be shared meaningfully. Nullable and backfilled rather than
-- NOT NULL, because articles already exist and a migration that fails on them helps nobody.
--
-- Gallery keys are newline-delimited in one TEXT column, matching how support_tickets stores its
-- image keys. A join table would be the textbook answer; it would also be a second table, a second
-- repository and a second ordering problem for a list that is never queried independently.
--
-- Same to_regclass DO-block guard as V42-V46.
DO $$
BEGIN
    IF to_regclass('public.news_articles') IS NOT NULL THEN

        ALTER TABLE news_articles ADD COLUMN IF NOT EXISTS slug VARCHAR(320);
        ALTER TABLE news_articles ADD COLUMN IF NOT EXISTS gallery_keys TEXT;

        -- Backfill: lowercase the title, drop anything that is not a letter, digit or space,
        -- collapse whitespace to single hyphens, then suffix the id's first block so two articles
        -- with the same title cannot collide.
        UPDATE news_articles
           SET slug = left(
                 regexp_replace(
                   regexp_replace(lower(title), '[^a-z0-9\s-]', '', 'g'),
                   '\s+', '-', 'g'
                 ), 280) || '-' || split_part(id::text, '-', 1)
         WHERE slug IS NULL;

        CREATE UNIQUE INDEX IF NOT EXISTS uq_news_articles_slug ON news_articles (slug);
        -- The public list reads published articles newest first; this is that query.
        CREATE INDEX IF NOT EXISTS idx_news_articles_published
            ON news_articles (status, published_at DESC);

    END IF;
END $$;
