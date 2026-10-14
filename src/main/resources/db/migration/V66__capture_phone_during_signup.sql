-- Carry signup contact through email verification, then persist it on the account.
DO $$
BEGIN
    IF to_regclass('public.email_otp') IS NOT NULL THEN
        ALTER TABLE email_otp ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32);
    END IF;
END $$;
