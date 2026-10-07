DO $$
BEGIN
    IF to_regclass('public.auth_credentials') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_cart_activity_credentials_user ON auth_credentials (user_id);
    END IF;
    IF to_regclass('public.carts') IS NOT NULL THEN
        ALTER TABLE carts ADD COLUMN IF NOT EXISTS last_added_at TIMESTAMPTZ;
        IF to_regclass('public.cart_items') IS NOT NULL THEN
            -- Approximation for historical rows; future adds have exact timestamps.
            UPDATE carts c SET last_added_at = x.added_at
            FROM (SELECT cart_id, MAX(created_at) AS added_at FROM cart_items GROUP BY cart_id) x
            WHERE c.id = x.cart_id AND c.last_added_at IS NULL;
        END IF;
        CREATE INDEX IF NOT EXISTS idx_carts_last_added ON carts (auth_credential_id, last_added_at DESC);
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS admin_cart_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    admin_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    email_status VARCHAR(20) NOT NULL DEFAULT 'SENDING',
    notification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_admin_cart_messages_user ON admin_cart_messages(user_id, created_at DESC);
