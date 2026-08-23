-- Customer support tickets: software bugs, "I'm stuck", order/payment/account trouble.
-- The Hibernate entities (SupportTicket / SupportTicketMessage) are the source of truth; this
-- migration keeps Flyway-managed schemas in sync, exactly like repair_requests in V23.
--
-- Wrapped in the same to_regclass DO-block guard as V23/V28: Flyway runs BEFORE Hibernate
-- ddl-auto, so on a fresh DB the migration is a safe no-op (Hibernate creates the schema from
-- the entities) while on the existing prod DB it creates the tables.
DO $$
BEGIN
    IF to_regclass('public.auth_credentials') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS support_tickets (
          id UUID PRIMARY KEY,
          reference VARCHAR(30),                     -- display ref, e.g. ST-2026-001
          credential_id UUID NOT NULL,               -- owner (sub / auth_credentials.id)
          user_id UUID,                              -- users.id (uid)
          category VARCHAR(30) NOT NULL,             -- SOFTWARE_BUG|ORDER_ISSUE|PAYMENT_ISSUE|ACCOUNT_ISSUE|OTHER
          subject VARCHAR(150) NOT NULL,
          description TEXT NOT NULL,
          page_url VARCHAR(500),                     -- where the customer got stuck (optional)
          image_keys TEXT,                           -- up to 4 Contabo keys, newline-delimited
          status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
                                                     -- OPEN|IN_PROGRESS|WAITING_FOR_CUSTOMER|RESOLVED|CLOSED
          admin_note TEXT,                           -- last team note / resolution text
          updated_by UUID,                           -- admin users.id (uid) of last update
          contact_email VARCHAR(255),                -- snapshotted from profile at submit
          admin_unread BOOLEAN NOT NULL DEFAULT true, -- drives the dashboard badge
          customer_unread BOOLEAN NOT NULL DEFAULT false,
          resolved_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        CREATE INDEX IF NOT EXISTS idx_support_tickets_credential ON support_tickets(credential_id);
        CREATE INDEX IF NOT EXISTS idx_support_tickets_status ON support_tickets(status);
        CREATE INDEX IF NOT EXISTS idx_support_tickets_admin_unread ON support_tickets(admin_unread);
        CREATE INDEX IF NOT EXISTS idx_support_tickets_created_at ON support_tickets(created_at DESC);

        -- The conversation on a ticket: customer replies and team replies/notes, oldest first.
        CREATE TABLE IF NOT EXISTS support_ticket_messages (
          id UUID PRIMARY KEY,
          ticket_id UUID NOT NULL,
          author VARCHAR(10) NOT NULL,               -- CUSTOMER|ADMIN
          author_user_id UUID,                       -- users.id of the writer (either side)
          body TEXT NOT NULL,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        CREATE INDEX IF NOT EXISTS idx_support_ticket_messages_ticket ON support_ticket_messages(ticket_id, created_at);

    END IF;
END $$;
