-- Security fix: the seed admin's password ("ChangeMe!123") was committed to source control in V1
-- (and documented in plaintext in README) — it must be treated as permanently leaked, since removing
-- it from a past migration is unsafe (breaks Flyway checksums for anyone who already ran V1) and git
-- history can't be un-leaked by editing HEAD. Instead: force a password change before this account can
-- do anything else, closing the hole even though the old password remains publicly known.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Only flag the seeded admin if it STILL has the exact leaked default hash — an operator who already
-- rotated the password is left untouched.
UPDATE users
SET must_change_password = TRUE
WHERE email = 'admin@mailstock.store'
  AND password_hash = '$2a$12$4wV5b3G0eSg1qEwF2eBt9uKfM6q6JmB4V1BqXqZfM4H9r9jI8Yq1G';
