-- Carry the account's creation year from the seller submission onto the inventory item so
-- buyers can see it before purchase (browse cards / dashboard / product page).
ALTER TABLE inventory_items ADD COLUMN account_creation_year INTEGER;

-- Backfill from the originating submissions where we have them.
UPDATE inventory_items i
SET account_creation_year = s.account_creation_year
FROM seller_submissions s
WHERE i.submission_id = s.id AND s.account_creation_year IS NOT NULL;
