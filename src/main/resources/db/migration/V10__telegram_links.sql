-- Binds a Telegram chat to a MailStock user account. One chat <-> one user.
CREATE TABLE telegram_links (
    chat_id     BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
