-- Google Authenticator (TOTP) two-factor auth for privileged (admin/supplier) accounts.
-- One row per user. The shared secret is stored ENCRYPTED (AES-GCM) — never hashed,
-- because the server must reproduce the one-time codes to verify them.
-- IF NOT EXISTS guards let Flyway co-exist with Hibernate ddl-auto on dev databases.
CREATE TABLE IF NOT EXISTS mfa_credentials (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL,
    secret_encrypted VARCHAR(512) NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    confirmed_at     TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ,
    CONSTRAINT uq_mfa_credentials_user_id UNIQUE (user_id)
);

-- Single-use backup codes shown once at enrollment. Only the SHA-256 hash is stored.
CREATE TABLE IF NOT EXISTS mfa_recovery_codes (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    used       BOOLEAN     NOT NULL DEFAULT FALSE,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mfa_recovery_codes_user_id ON mfa_recovery_codes (user_id);
