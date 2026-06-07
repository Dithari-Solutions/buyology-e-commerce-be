-- Per-store courier profiles (each store manages its own delivery couriers).
CREATE TABLE IF NOT EXISTS courier_profiles (
    id           UUID PRIMARY KEY,
    store_id     UUID NOT NULL,
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100),
    phone        VARCHAR(30) NOT NULL,
    email        VARCHAR(200),
    vehicle_type VARCHAR(30),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_courier_profile_store ON courier_profiles (store_id);
