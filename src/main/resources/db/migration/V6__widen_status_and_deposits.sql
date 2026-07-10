-- Status enum values like 'AWAITING_VERIFICATION' (21 chars) overflow VARCHAR(20). Widen them.
ALTER TABLE orders             ALTER COLUMN status       TYPE VARCHAR(40);
ALTER TABLE payments           ALTER COLUMN status       TYPE VARCHAR(40);
ALTER TABLE inventory_items    ALTER COLUMN stock_status TYPE VARCHAR(40);
ALTER TABLE seller_submissions ALTER COLUMN status       TYPE VARCHAR(40);
ALTER TABLE withdraw_requests  ALTER COLUMN status       TYPE VARCHAR(40);

-- Buyer balance deposits: buyer requests a top-up, admin verifies, balance is credited.
CREATE TABLE wallet_deposits (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount       NUMERIC(14,2) NOT NULL,
    txid         VARCHAR(200),
    method       VARCHAR(30)  NOT NULL DEFAULT 'BINANCE_MANUAL',
    status       VARCHAR(40)  NOT NULL DEFAULT 'PENDING',
    admin_note   TEXT,
    processed_by BIGINT REFERENCES users(id),
    processed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_deposit_user   ON wallet_deposits(user_id);
CREATE INDEX idx_deposit_status ON wallet_deposits(status);

-- Public deposit instructions shown to buyers (edit in Admin → Settings)
INSERT INTO settings (key, value) VALUES
  ('deposit.wallet_address', 'TXxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'),
  ('deposit.network',        'TRC20-USDT')
ON CONFLICT (key) DO NOTHING;
