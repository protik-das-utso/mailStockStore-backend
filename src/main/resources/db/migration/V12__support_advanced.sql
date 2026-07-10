-- Advanced support: order context, activity + unread tracking.
ALTER TABLE support_tickets ADD COLUMN order_id          BIGINT;
ALTER TABLE support_tickets ADD COLUMN last_message_at   TIMESTAMPTZ;
ALTER TABLE support_tickets ADD COLUMN last_sender_staff BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE support_tickets ADD COLUMN user_read_at      TIMESTAMPTZ;
ALTER TABLE support_tickets ADD COLUMN admin_read_at     TIMESTAMPTZ;

-- Backfill last activity from the newest message (fallback to ticket timestamps).
UPDATE support_tickets t SET last_message_at = COALESCE(
    (SELECT MAX(m.created_at) FROM ticket_messages m WHERE m.ticket_id = t.id),
    t.updated_at, t.created_at);

-- (RESOLVED is a new enum value; status column is varchar, no change needed.)
