-- Carry account type/country onto inventory so stock can be counted per Gmail type
ALTER TABLE inventory_items
    ADD COLUMN account_type VARCHAR(10),
    ADD COLUMN country      VARCHAR(80);

-- Backfill from the originating submission where possible
UPDATE inventory_items i
SET account_type = s.account_type,
    country      = s.country
FROM seller_submissions s
WHERE i.submission_id = s.id;

-- Selling price to buyers + target ("needed") stock level per type, for the pricing panel
INSERT INTO settings (key, value) VALUES
  ('sell.gmail_old',    '4.50'),
  ('sell.gmail_new',    '2.50'),
  ('stock.target_old',  '20'),
  ('stock.target_new',  '20');
