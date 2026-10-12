-- Temporary customer policy: checkout does not require SMS verification.
-- Update existing profiles so web and mobile verification gates agree.
-- Hibernate creates this table after Flyway on a fresh installation.
DO $$
BEGIN
    IF to_regclass('public.user_profiles') IS NOT NULL THEN
        ALTER TABLE user_profiles ALTER COLUMN phone_verified SET DEFAULT TRUE;
        UPDATE user_profiles SET phone_verified = TRUE WHERE phone_verified = FALSE;
    END IF;
END $$;
