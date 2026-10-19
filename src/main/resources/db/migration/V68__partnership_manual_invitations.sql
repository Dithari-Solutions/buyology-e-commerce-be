CREATE TABLE partnership_invitation_batches (
    id UUID PRIMARY KEY,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    template_version VARCHAR(32) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    html_body TEXT NOT NULL
);
CREATE TABLE partnership_invitations (
    batch_id UUID NOT NULL REFERENCES partnership_invitation_batches(id),
    email VARCHAR(254) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED','SENDING','ACCEPTED','FAILED','UNKNOWN')),
    attempted_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    PRIMARY KEY (batch_id, email)
);
CREATE INDEX idx_partnership_invitation_created ON partnership_invitation_batches(created_at DESC);
CREATE INDEX idx_partnership_invitation_queue ON partnership_invitations(batch_id) WHERE status = 'QUEUED';
