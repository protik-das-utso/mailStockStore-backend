-- Per-user coupon cap + redemption ledger (enforces "N per customer"), and
-- persisted Telegram link codes so codes survive restarts / work across instances.

-- 1. Optional per-user redemption cap on a coupon (NULL = unlimited per user).
ALTER TABLE coupons ADD COLUMN per_user_limit INTEGER;

-- 2. One row per successful coupon redemption by a user (used to enforce per_user_limit).
CREATE TABLE coupon_redemptions (
    id         BIGSERIAL PRIMARY KEY,
    coupon_id  BIGINT NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_id   BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_coupon_redemption_user ON coupon_redemptions (coupon_id, user_id);

-- 3. Website-minted one-time link codes, redeemed in the bot to bind a chat to an account.
CREATE TABLE telegram_link_codes (
    code       VARCHAR(16) PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_telegram_link_code_expires ON telegram_link_codes (expires_at);
