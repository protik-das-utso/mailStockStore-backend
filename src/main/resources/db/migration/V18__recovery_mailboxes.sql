-- Self-serve recovery-code viewer. A platform-owned mailbox (e.g. b@ttigerbd.com) is set as the
-- recovery email on sold Google accounts. When someone adds it, Google emails a verification code to
-- that mailbox; the backend reads it over POP3 and exposes ONLY the code (plus time) behind an
-- unguessable, per-account public link. See store.mailstock.recovery.

-- One platform-controlled mailbox we can read via POP3.
CREATE TABLE recovery_mailboxes (
    id                BIGSERIAL PRIMARY KEY,
    label             VARCHAR(120) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    host              VARCHAR(255) NOT NULL,
    port              INTEGER      NOT NULL DEFAULT 995,
    ssl               BOOLEAN      NOT NULL DEFAULT TRUE,
    username          VARCHAR(255) NOT NULL,
    -- POP3 password, AES-GCM encrypted at rest (see recovery.CryptoService). NEVER returned by any API.
    password_enc      TEXT         NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One public, unguessable link scoped to a single account so it can only ever surface that
-- account's code, even though many accounts share one mailbox.
CREATE TABLE recovery_links (
    id                BIGSERIAL PRIMARY KEY,
    token             VARCHAR(64)  NOT NULL UNIQUE,
    mailbox_id        BIGINT       NOT NULL REFERENCES recovery_mailboxes(id) ON DELETE CASCADE,
    -- The Google account whose recovery email is being set (e.g. aliakber9786@gmail.com). The code
    -- reader matches messages naming this address, so links never cross accounts.
    account_email     VARCHAR(255) NOT NULL,
    -- Optional linkage back to what was sold, for admin context / auditing.
    inventory_id      BIGINT,
    order_item_id     BIGINT,
    revoked           BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_recovery_links_token ON recovery_links(token);
