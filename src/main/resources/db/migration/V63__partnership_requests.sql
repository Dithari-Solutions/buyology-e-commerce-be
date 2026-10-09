CREATE TABLE partnership_requests (
    id UUID PRIMARY KEY,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW','REVIEWED','RESPONDED')),
    admin_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    email_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (email_status IN ('PENDING','SENT','FAILED')),
    email_attempts INTEGER NOT NULL DEFAULT 0,
    next_email_attempt TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_partnership_created ON partnership_requests (created_at DESC);
CREATE INDEX idx_partnership_email_pending ON partnership_requests (next_email_attempt) WHERE email_status = 'PENDING';
