-- The B2B application's "number of employees" is now a company-size range bucket
-- (e.g. "51-200", "10001+") chosen from a fixed dropdown, not a raw count. Widen the
-- column from integer to text; any pre-existing integer values are cast to their text
-- form (e.g. 50 -> "50"), which still renders correctly for admins.
--
-- Guarded with to_regclass because Flyway runs BEFORE Hibernate ddl-auto: on a fresh DB
-- the table may not exist yet (Hibernate then creates it as text from the entity). On an
-- existing DB (dev ddl-auto=update or prod validate) this alters the existing column.
DO $$
BEGIN
    IF to_regclass('public.b2b_membership_applications') IS NOT NULL THEN
        ALTER TABLE b2b_membership_applications
            ALTER COLUMN number_of_employees TYPE VARCHAR(20)
            USING number_of_employees::text;
    END IF;
END $$;
