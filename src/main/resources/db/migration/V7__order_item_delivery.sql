-- Snapshot the delivered credentials onto each order line at sale time, so a buyer
-- can always retrieve exactly what they purchased (independent of later inventory edits).
ALTER TABLE order_items ADD COLUMN delivery_payload TEXT;
