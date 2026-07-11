-- Abuse auto-flagging: a buyer who crosses the warranty-claim or failed-deposit threshold is flagged
-- for admin review (login is NOT blocked — flag + notify only). Thresholds live in settings so an admin
-- can tune them without a redeploy.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS flagged        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS flagged_reason TEXT,
    ADD COLUMN IF NOT EXISTS flagged_at     TIMESTAMPTZ;

-- Default thresholds (lenient): flag after 5 lifetime warranty claims OR 5 rejected deposits.
INSERT INTO settings (key, value) VALUES
    ('abuse.warranty_claim_limit', '5'),
    ('abuse.failed_deposit_limit', '5')
ON CONFLICT (key) DO NOTHING;
