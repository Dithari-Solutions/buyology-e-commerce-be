-- Supplier-initiated, superadmin-approved product change requests (edit/delete/restore).
CREATE TABLE IF NOT EXISTS supplier_product_change_requests (
    id               UUID PRIMARY KEY,
    product_id       UUID NOT NULL,
    supplier_id      UUID NOT NULL,
    action           VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payload          TEXT,
    rejection_reason TEXT,
    requested_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at      TIMESTAMPTZ,
    reviewed_by      UUID
);

CREATE INDEX IF NOT EXISTS idx_spcr_supplier_id ON supplier_product_change_requests (supplier_id);
CREATE INDEX IF NOT EXISTS idx_spcr_product_id ON supplier_product_change_requests (product_id);
CREATE INDEX IF NOT EXISTS idx_spcr_status ON supplier_product_change_requests (status);
