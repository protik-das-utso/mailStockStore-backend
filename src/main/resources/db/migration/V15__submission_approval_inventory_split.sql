-- Split "approve" from "add to inventory": approval stores the agreed deal on the submission,
-- and a separate step turns it into a sellable inventory item.
ALTER TABLE seller_submissions
    ADD COLUMN purchase_price   NUMERIC(14,2),
    ADD COLUMN selling_price    NUMERIC(14,2),
    ADD COLUMN delivery_payload TEXT,
    ADD COLUMN internal_notes   TEXT,
    ADD COLUMN inventory_id     BIGINT;
