-- Storefront AI assistant: conversation transcripts.
--
-- The widget is public and unauthenticated, and the client holds nothing but a conversation id — the
-- transcript itself lives here and is replayed from these tables on every turn. That is what stops a
-- caller forging the assistant's previous turns, which is the usual way a scoped support bot gets
-- talked out of its scope.
--
-- Two things are read back out of these tables in the dashboard (/api/admin/assistant/**): what
-- customers asked, which is a direct readout of what the storefront fails to explain, and what the
-- assistant answered, which the business is on the hook for. assistant_messages.in_scope records the
-- turns where it declined an off-topic question — an assistant that starts declining real product
-- questions is a regression visible nowhere else.
--
-- No IP address or personal identifier is stored. visitor_id is the same opaque browser id the
-- visitor beacon uses, kept only to enforce the per-visitor daily conversation cap.
--
-- Like site_visits, assistant_messages grows with traffic rather than with business events. There is
-- deliberately no purge job yet: transcript volume is unknown until the widget is live, and the
-- retention period is a business decision (these are customer-written free text), not a default to
-- pick blind. Revisit once there is a month of real traffic.
--
-- The entities are the source of truth; this migration keeps Flyway-managed (prod) schemas in sync.
-- Wrapped in a to_regclass DO-block guard because Flyway runs BEFORE Hibernate ddl-auto: on a fresh
-- DB (and in FlywayBaselineMigrationIT) Hibernate creates the schema from the entities later, so the
-- guard makes this a safe no-op there and only creates the tables on the existing prod DB. Anchored
-- on `orders` (a stable core table present on prod). Mirrors V17/V18/V21/V22/V30.
DO $$
BEGIN
    IF to_regclass('public.orders') IS NOT NULL THEN

        CREATE TABLE IF NOT EXISTS assistant_conversations (
          id UUID PRIMARY KEY,
          visitor_id VARCHAR(64),
          user_id UUID,
          language VARCHAR(8),
          country_code VARCHAR(2),
          currency VARCHAR(8),
          message_count INTEGER NOT NULL DEFAULT 0,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
          last_message_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        -- last_message_at DESC: the admin list order. visitor_id: the per-visitor daily cap, which
        -- is checked before every new conversation is opened.
        CREATE INDEX IF NOT EXISTS idx_assistant_conversations_last_message
            ON assistant_conversations(last_message_at DESC);
        CREATE INDEX IF NOT EXISTS idx_assistant_conversations_visitor
            ON assistant_conversations(visitor_id);

        CREATE TABLE IF NOT EXISTS assistant_messages (
          id UUID PRIMARY KEY,
          conversation_id UUID NOT NULL REFERENCES assistant_conversations(id),
          role VARCHAR(16) NOT NULL,
          content TEXT NOT NULL,
          in_scope BOOLEAN,
          product_ids TEXT,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );

        -- (conversation, created_at) serves both reads there are: replaying a transcript for the
        -- model and rendering one in the dashboard, both oldest-turn-first.
        CREATE INDEX IF NOT EXISTS idx_assistant_messages_conversation
            ON assistant_messages(conversation_id, created_at);

    END IF;
END $$;
