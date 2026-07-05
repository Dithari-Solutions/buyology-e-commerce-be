-- Two rapid add-to-cart requests could each find no ACTIVE cart and create one
-- (findOrCreateActiveCart had no DB-level guard), producing TWO active carts for the
-- same credential. Items then split across carts and the GET only shows one — the
-- other product was "added" (200) but invisible and absent at checkout.
--
-- Make "at most one ACTIVE cart per auth_credential_id" a hard invariant.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh
-- database (and in FlywayBaselineMigrationIT) the carts table doesn't exist yet — Hibernate
-- creates it later from the entity — so unguarded DDL/DML against it fails with
-- "relation carts does not exist". On the existing prod DB this cleans up duplicates and
-- adds the constraint. Mirrors the guard used by V3/V14 and the other patch migrations.
DO $$
BEGIN
    IF to_regclass('public.carts') IS NOT NULL THEN
        -- 1. Clean up any existing duplicates: keep the most-recently-updated ACTIVE cart
        --    per credential, demote the rest to ABANDONED (their items were already invisible).
        UPDATE carts c
        SET status = 'ABANDONED',
            updated_at = now()
        WHERE c.status = 'ACTIVE'
          AND c.id <> (
            SELECT c2.id
            FROM carts c2
            WHERE c2.auth_credential_id = c.auth_credential_id
              AND c2.status = 'ACTIVE'
            ORDER BY c2.updated_at DESC, c2.id DESC
            LIMIT 1
          );

        -- 2. Enforce it going forward. A partial unique index lets CHECKED_OUT/ABANDONED
        --    carts accumulate but allows only ONE ACTIVE row per credential — a concurrent
        --    second insert now fails the constraint (and is retried/surfaced) instead of
        --    silently corrupting the cart.
        CREATE UNIQUE INDEX IF NOT EXISTS ux_cart_active_per_credential
            ON carts (auth_credential_id)
            WHERE status = 'ACTIVE';
    END IF;
END $$;
