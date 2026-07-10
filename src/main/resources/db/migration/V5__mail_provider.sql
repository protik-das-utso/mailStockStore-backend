-- Support Outlook accounts alongside Gmail: a provider dimension on submissions & inventory
ALTER TABLE seller_submissions ADD COLUMN provider VARCHAR(10) NOT NULL DEFAULT 'GMAIL';
ALTER TABLE inventory_items    ADD COLUMN provider VARCHAR(10) NOT NULL DEFAULT 'GMAIL';

-- Pricing/stock settings are now keyed per provider+type: <prefix>.<provider>_<type>
-- Gmail payout/sell keys (price.gmail_*, sell.gmail_*) already exist from V2/V3.
INSERT INTO settings (key, value) VALUES
  ('price.outlook_old', '2.50'),
  ('price.outlook_new', '1.00'),
  ('sell.outlook_old',  '5.00'),
  ('sell.outlook_new',  '2.50'),
  ('stock.target_gmail_old',   '20'),
  ('stock.target_gmail_new',   '20'),
  ('stock.target_outlook_old', '20'),
  ('stock.target_outlook_new', '20')
ON CONFLICT (key) DO NOTHING;

-- Retire the old provider-less target keys
DELETE FROM settings WHERE key IN ('stock.target_old', 'stock.target_new');
