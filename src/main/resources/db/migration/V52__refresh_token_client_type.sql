-- Which client a refresh-token session belongs to, so rotating it cannot change the answer.
--
-- The access token's audience decides whether a privileged account is authenticated at all:
-- JwtAuthenticationFilter drops the authentication of any ADMIN/SUPPLIER/SUPERADMIN/
-- CUSTOMER_SUPPORT/PROCUREMENT/REPAIR principal whose token is not audience "dashboard".
--
-- That audience was taken from the X-Client-Type request header on EVERY call, including
-- /auth/refresh, and defaulted to "web" when the header was absent. So one refresh sent without
-- the header re-minted an admin's access token as "web", the filter then treated every following
-- request as anonymous, and the dashboard signed the admin out — on an ordinary page reload,
-- which is exactly when a refresh happens.
--
-- The session's client type now lives here, written once when the refresh token is issued, and
-- rotation reads it back instead of re-deriving it from a header the client may not resend.
-- Existing rows are NULL, which keeps the old header-derived behaviour for sessions issued before
-- this column existed; they heal on the next sign-in.
--
-- Same to_regclass DO-block guard as V42/V43/V45: Flyway runs BEFORE Hibernate ddl-auto, so on a
-- fresh DB this is a safe no-op (Hibernate creates the column from the entity) while on the
-- existing prod DB it adds the column.
DO $$
BEGIN
    IF to_regclass('public.refresh_tokens') IS NOT NULL THEN
        ALTER TABLE refresh_tokens
            ADD COLUMN IF NOT EXISTS client_type VARCHAR(20);
    END IF;
END $$;
