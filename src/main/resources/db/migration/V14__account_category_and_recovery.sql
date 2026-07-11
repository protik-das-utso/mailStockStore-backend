-- Buyer-facing account taxonomy (age + 2FA) replacing the OLD/NEW-only type, plus optional backup codes.

ALTER TABLE seller_submissions
    ADD COLUMN account_category VARCHAR(20),
    ADD COLUMN backup_codes     TEXT;

ALTER TABLE inventory_items
    ADD COLUMN account_category VARCHAR(20);

-- Backfill existing rows from the legacy OLD/NEW type so old listings still carry a category.
UPDATE seller_submissions SET account_category = CASE account_type
        WHEN 'NEW' THEN 'NEW_NO_2FA'
        WHEN 'OLD' THEN 'Y1_3'
        ELSE 'NEW_NO_2FA' END
    WHERE account_category IS NULL;

UPDATE inventory_items SET account_category = CASE account_type
        WHEN 'NEW' THEN 'NEW_NO_2FA'
        WHEN 'OLD' THEN 'Y1_3'
        ELSE 'NEW_NO_2FA' END
    WHERE account_category IS NULL;

-- Store recovery email that sellers must add to every account (editable in Admin → Settings).
INSERT INTO settings (key, value) VALUES ('store.recovery_email', 'recovery@mailstock.store')
    ON CONFLICT (key) DO NOTHING;

-- Payout (price.*) / sell (sell.*) / target-stock (stock.target_*) per provider × category.
INSERT INTO settings (key, value) VALUES
    -- Gmail
    ('price.gmail_new_no_2fa',  '1.00'), ('sell.gmail_new_no_2fa',  '2.00'), ('stock.target_gmail_new_no_2fa',  '20'),
    ('price.gmail_new_2fa',     '1.50'), ('sell.gmail_new_2fa',     '2.75'), ('stock.target_gmail_new_2fa',     '20'),
    ('price.gmail_m3_12_no_2fa','2.00'), ('sell.gmail_m3_12_no_2fa','3.50'), ('stock.target_gmail_m3_12_no_2fa','20'),
    ('price.gmail_m3_12_2fa',   '2.50'), ('sell.gmail_m3_12_2fa',   '4.25'), ('stock.target_gmail_m3_12_2fa',   '20'),
    ('price.gmail_y1_3',        '3.50'), ('sell.gmail_y1_3',        '6.00'), ('stock.target_gmail_y1_3',        '20'),
    ('price.gmail_y3_plus',     '5.00'), ('sell.gmail_y3_plus',     '8.50'), ('stock.target_gmail_y3_plus',     '20'),
    -- Outlook
    ('price.outlook_new_no_2fa',  '0.80'), ('sell.outlook_new_no_2fa',  '1.75'), ('stock.target_outlook_new_no_2fa',  '20'),
    ('price.outlook_new_2fa',     '1.20'), ('sell.outlook_new_2fa',     '2.40'), ('stock.target_outlook_new_2fa',     '20'),
    ('price.outlook_m3_12_no_2fa','1.60'), ('sell.outlook_m3_12_no_2fa','3.00'), ('stock.target_outlook_m3_12_no_2fa','20'),
    ('price.outlook_m3_12_2fa',   '2.00'), ('sell.outlook_m3_12_2fa',   '3.75'), ('stock.target_outlook_m3_12_2fa',   '20'),
    ('price.outlook_y1_3',        '3.00'), ('sell.outlook_y1_3',        '5.25'), ('stock.target_outlook_y1_3',        '20'),
    ('price.outlook_y3_plus',     '4.50'), ('sell.outlook_y3_plus',     '7.50'), ('stock.target_outlook_y3_plus',     '20')
ON CONFLICT (key) DO NOTHING;
