-- =========================================================
-- MailStock.store — initial schema
-- =========================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---- Users & auth ----
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(190) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120),
    phone           VARCHAR(32),
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    locked          BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE email_verification_tokens (
    id         BIGSERIAL PRIMARY KEY,
    token      VARCHAR(128) NOT NULL UNIQUE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_evt_token ON email_verification_tokens(token);

CREATE TABLE password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    token      VARCHAR(128) NOT NULL UNIQUE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_prt_token ON password_reset_tokens(token);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    token      VARCHAR(512) NOT NULL UNIQUE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rt_token ON refresh_tokens(token);

-- ---- Seller submissions ----
CREATE TABLE seller_submissions (
    id                BIGSERIAL PRIMARY KEY,
    seller_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title             VARCHAR(200) NOT NULL,
    category          VARCHAR(50)  NOT NULL,
    description       TEXT,
    asking_price      NUMERIC(14,2) NOT NULL,
    warranty_days     INT NOT NULL DEFAULT 0,
    supporting_files  TEXT,
    notes             TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    counter_price     NUMERIC(14,2),
    admin_note        TEXT,
    reviewed_by       BIGINT REFERENCES users(id),
    reviewed_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ
);
CREATE INDEX idx_sub_seller ON seller_submissions(seller_id);
CREATE INDEX idx_sub_status ON seller_submissions(status);

-- ---- Inventory ----
CREATE TABLE inventory_items (
    id             BIGSERIAL PRIMARY KEY,
    submission_id  BIGINT REFERENCES seller_submissions(id),
    seller_id      BIGINT REFERENCES users(id),
    title          VARCHAR(200) NOT NULL,
    category       VARCHAR(50)  NOT NULL,
    description    TEXT,
    purchase_price NUMERIC(14,2) NOT NULL,
    selling_price  NUMERIC(14,2) NOT NULL,
    warranty_days  INT NOT NULL DEFAULT 0,
    stock_status   VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    delivery_payload TEXT,
    internal_notes TEXT,
    purchase_date  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX idx_inv_status ON inventory_items(stock_status);
CREATE INDEX idx_inv_category ON inventory_items(category);

-- ---- Orders / payments ----
CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    buyer_id         BIGINT NOT NULL REFERENCES users(id),
    total_amount     NUMERIC(14,2) NOT NULL,
    discount_amount  NUMERIC(14,2) NOT NULL DEFAULT 0,
    coupon_code      VARCHAR(60),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING_PAYMENT',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ
);
CREATE INDEX idx_orders_buyer ON orders(buyer_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_items (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    inventory_id   BIGINT NOT NULL REFERENCES inventory_items(id),
    title          VARCHAR(200) NOT NULL,
    price          NUMERIC(14,2) NOT NULL,
    warranty_days  INT NOT NULL DEFAULT 0,
    warranty_expires_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_oi_order ON order_items(order_id);

CREATE TABLE payments (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    method       VARCHAR(30) NOT NULL DEFAULT 'BINANCE_MANUAL',
    amount       NUMERIC(14,2) NOT NULL,
    txid         VARCHAR(200),
    network      VARCHAR(50),
    wallet       VARCHAR(200),
    status       VARCHAR(20) NOT NULL DEFAULT 'AWAITING_TXID',
    admin_note   TEXT,
    verified_by  BIGINT REFERENCES users(id),
    verified_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ
);
CREATE INDEX idx_payments_status ON payments(status);

-- ---- Wallets / withdrawals ----
CREATE TABLE wallets (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    pending_balance    NUMERIC(14,2) NOT NULL DEFAULT 0,
    available_balance  NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_earnings     NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ
);

CREATE TABLE wallet_transactions (
    id          BIGSERIAL PRIMARY KEY,
    wallet_id   BIGINT NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,
    amount      NUMERIC(14,2) NOT NULL,
    balance_after NUMERIC(14,2) NOT NULL,
    reference   VARCHAR(120),
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_wt_wallet ON wallet_transactions(wallet_id);

CREATE TABLE withdraw_requests (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    amount        NUMERIC(14,2) NOT NULL,
    method        VARCHAR(30) NOT NULL DEFAULT 'BINANCE',
    destination   VARCHAR(200) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note    TEXT,
    processed_by  BIGINT REFERENCES users(id),
    processed_at  TIMESTAMPTZ,
    payout_txid   VARCHAR(200),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ
);
CREATE INDEX idx_wr_status ON withdraw_requests(status);

-- ---- Warranty ----
CREATE TABLE warranty_claims (
    id             BIGSERIAL PRIMARY KEY,
    order_item_id  BIGINT NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    buyer_id       BIGINT NOT NULL REFERENCES users(id),
    reason         VARCHAR(60) NOT NULL,
    description    TEXT,
    evidence_url   TEXT,
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution     VARCHAR(30),
    admin_note     TEXT,
    resolved_by    BIGINT REFERENCES users(id),
    resolved_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX idx_wc_status ON warranty_claims(status);

-- ---- Support ----
CREATE TABLE support_tickets (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    subject     VARCHAR(200) NOT NULL,
    category    VARCHAR(50),
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority    VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    closed_at   TIMESTAMPTZ
);
CREATE INDEX idx_st_status ON support_tickets(status);

CREATE TABLE ticket_messages (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender_id    BIGINT NOT NULL REFERENCES users(id),
    body         TEXT NOT NULL,
    attachment_url TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tm_ticket ON ticket_messages(ticket_id);

-- ---- Notifications ----
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    link       VARCHAR(300),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_user ON notifications(user_id);

-- ---- Coupons ----
CREATE TABLE coupons (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(60) NOT NULL UNIQUE,
    discount_type  VARCHAR(20) NOT NULL,
    discount_value NUMERIC(14,2) NOT NULL,
    max_uses       INT,
    used_count     INT NOT NULL DEFAULT 0,
    min_amount     NUMERIC(14,2),
    starts_at      TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---- Announcements ----
CREATE TABLE announcements (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    body       TEXT NOT NULL,
    audience   VARCHAR(20) NOT NULL DEFAULT 'ALL',
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at  TIMESTAMPTZ,
    ends_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---- Settings (key/value) ----
CREATE TABLE settings (
    key        VARCHAR(80) PRIMARY KEY,
    value      TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---- Audit logs ----
CREATE TABLE audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    actor_id   BIGINT REFERENCES users(id),
    action     VARCHAR(80) NOT NULL,
    entity     VARCHAR(80),
    entity_id  VARCHAR(80),
    metadata   TEXT,
    ip         VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_al_actor ON audit_logs(actor_id);
CREATE INDEX idx_al_action ON audit_logs(action);

-- ---- Reviews (for product page + homepage reviews) ----
CREATE TABLE reviews (
    id            BIGSERIAL PRIMARY KEY,
    inventory_id  BIGINT REFERENCES inventory_items(id) ON DELETE CASCADE,
    buyer_id      BIGINT NOT NULL REFERENCES users(id),
    rating        INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body          TEXT,
    approved      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reviews_inv ON reviews(inventory_id);

-- ---- Seed default admin (password: ChangeMe!123, bcrypt cost 12) ----
INSERT INTO users (email, password_hash, full_name, email_verified, enabled)
VALUES ('admin@mailstock.store',
        '$2a$12$4wV5b3G0eSg1qEwF2eBt9uKfM6q6JmB4V1BqXqZfM4H9r9jI8Yq1G',
        'Site Admin', TRUE, TRUE);

INSERT INTO user_roles (user_id, role)
SELECT id, 'ADMIN' FROM users WHERE email = 'admin@mailstock.store';

INSERT INTO settings (key, value) VALUES
  ('site.commission_percent', '10'),
  ('site.min_withdraw',       '10'),
  ('site.currency',           'USDT');
