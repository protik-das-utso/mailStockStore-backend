-- Persist admin-uploaded images (deposit QR, site logo, hero) in the database instead of the
-- container filesystem. On PaaS like Render the filesystem is ephemeral and wiped on every redeploy,
-- which reset the images; the DB survives redeploys, so an image is uploaded once and stays.
CREATE TABLE media_assets (
    name         VARCHAR(40) PRIMARY KEY,   -- e.g. deposit-qr, logo, hero
    content_type VARCHAR(100) NOT NULL,
    data         BYTEA        NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
