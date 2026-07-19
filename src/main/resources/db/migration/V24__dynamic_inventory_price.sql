-- Part 1: seller_submissions.offered_payout — snapshot the buying price at submit time so approval
-- pays what was offered then, not what the setting is now. Future setting changes affect only new submissions.
ALTER TABLE seller_submissions ADD COLUMN offered_payout DECIMAL(14,2);
COMMENT ON COLUMN seller_submissions.offered_payout IS 'Buying price (price.<provider>_<category>) offered at submission time, stamped on creation. Admin pays this on APPROVE. NULL for old rows (treated as askingPrice).';

-- Part 2: inventory_items.selling_price becomes nullable — NULL means "follow the live sell.<provider>_<category> setting"
-- so Price & Stocks changes show immediately on the browse page without editing every inventory row.
ALTER TABLE inventory_items ALTER COLUMN selling_price DROP NOT NULL;
COMMENT ON COLUMN inventory_items.selling_price IS 'Buyer price override. NULL = resolve from sell.<provider>_<category> setting at browse/order time (updates live when admin changes Price & Stocks). Set this to lock a specific item to a fixed price.';

-- Backfill: leave existing inventory rows with their current selling_price intact (the admin set them explicitly).
-- New inventory additions and direct admin-adds default to NULL so they track the setting.
