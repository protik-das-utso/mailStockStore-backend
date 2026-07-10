-- Email-account details for seller submissions
ALTER TABLE seller_submissions
    ADD COLUMN email_address         VARCHAR(255),
    ADD COLUMN email_password        VARCHAR(255),
    ADD COLUMN two_factor_code       VARCHAR(120),
    ADD COLUMN account_type          VARCHAR(10),
    ADD COLUMN country               VARCHAR(80),
    ADD COLUMN recovery_email        VARCHAR(255),
    ADD COLUMN phone_number          VARCHAR(30),
    ADD COLUMN account_creation_year INT,
    ADD COLUMN phone_verified        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN recovery_email_added  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN quantity              INT NOT NULL DEFAULT 1,
    ADD COLUMN additional_info       TEXT;

-- Admin-defined payout rate per Gmail (shown to sellers on the submission page)
INSERT INTO settings (key, value) VALUES
  ('price.gmail_old', '3.00'),
  ('price.gmail_new', '1.50');
