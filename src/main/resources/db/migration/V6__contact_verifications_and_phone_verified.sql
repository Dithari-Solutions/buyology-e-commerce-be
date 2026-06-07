-- Reusable contact-verification records (email via SendGrid, phone via Twilio Verify).
-- Used by the supplier apply, B2B membership apply and customer profile flows.
CREATE TABLE IF NOT EXISTS contact_verifications (
    id          UUID PRIMARY KEY,
    channel     VARCHAR(10)  NOT NULL,
    target      VARCHAR(255) NOT NULL,
    otp_code    VARCHAR(6),
    expires_at  TIMESTAMPTZ,
    attempts    INTEGER      NOT NULL DEFAULT 0,
    verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_contact_verif_channel_target ON contact_verifications (channel, target);
CREATE INDEX IF NOT EXISTS idx_contact_verif_expires_at ON contact_verifications (expires_at);

-- Track whether a customer's profile phone number has been verified via Twilio Verify.
ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
