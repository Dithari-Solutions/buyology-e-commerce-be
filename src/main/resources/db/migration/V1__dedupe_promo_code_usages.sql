-- Removes duplicate promo redemptions BEFORE Hibernate's ddl-auto adds the new
-- unique constraint uq_pcu_promo_user_order(promo_code_id, user_id, order_id).
-- Without this, adding that constraint to a table that already holds duplicate
-- rows aborts application startup.
--
-- Flyway runs this before Hibernate, so the table may not exist yet on a brand-new
-- database — the to_regclass guard makes the migration a no-op in that case.
-- Keeps the row with the lowest physical ctid for each duplicate group.
DO $$
BEGIN
    IF to_regclass('public.promo_code_usages') IS NOT NULL THEN
        DELETE FROM promo_code_usages a
        USING promo_code_usages b
        WHERE a.ctid < b.ctid
          AND a.promo_code_id = b.promo_code_id
          AND a.user_id       = b.user_id
          AND a.order_id      = b.order_id;
    END IF;
END $$;
