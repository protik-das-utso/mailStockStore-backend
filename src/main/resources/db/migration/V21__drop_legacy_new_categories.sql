-- Retire the two coarse "0–3 months" categories (NEW_NO_2FA / NEW_2FA). They overlap the finer
-- buckets added in 2026-07-11 (0d / 1–3d / 4–7d / 1–3mo), so the taxonomy now reads cleanly.
--
-- account_category is @Enumerated(EnumType.STRING) over VARCHAR, so any row still holding the old
-- string would make Hibernate throw once the enum constant is gone. V14 backfilled EVERY
-- pre-existing submission and inventory row to 'NEW_NO_2FA', so the remap below is load-bearing —
-- it must run before the enum values are removed from the code.

-- ---- 1. Move existing rows to the closest surviving bucket (1–3 months) ----
UPDATE seller_submissions SET account_category = 'NEW_1_3M_NO_2FA' WHERE account_category = 'NEW_NO_2FA';
UPDATE seller_submissions SET account_category = 'NEW_1_3M_2FA'    WHERE account_category = 'NEW_2FA';

UPDATE inventory_items    SET account_category = 'NEW_1_3M_NO_2FA' WHERE account_category = 'NEW_NO_2FA';
UPDATE inventory_items    SET account_category = 'NEW_1_3M_2FA'    WHERE account_category = 'NEW_2FA';

-- ---- 2. Carry the retired categories' pricing over to the surviving ones ----
-- Settings keys are '<prefix>.<provider>_<category>' (V14 seeded price./sell./stock.target_ for the
-- coarse categories; the finer ones were never seeded and fall back to code defaults). If an admin
-- priced the old category and never priced the 1–3mo one, that value would otherwise be lost —
-- payout/sell default to 0, which would list accounts for free. ON CONFLICT DO NOTHING means an
-- already-configured 1–3mo price always wins.
INSERT INTO settings (key, value)
SELECT replace(s.key, '_new_no_2fa', '_new_1_3m_no_2fa'), s.value
FROM settings s
WHERE s.key LIKE '%\_new\_no\_2fa'
ON CONFLICT (key) DO NOTHING;

INSERT INTO settings (key, value)
SELECT replace(s.key, '_new_2fa', '_new_1_3m_2fa'), s.value
FROM settings s
WHERE s.key LIKE '%\_new\_2fa'
ON CONFLICT (key) DO NOTHING;

-- ---- 3. Drop the now-orphaned settings rows ----
-- The LIKE patterns are anchored to the full suffix, so '..._new_1_3m_no_2fa' and '..._m3_12_no_2fa'
-- are not matched — only the exact retired keys are removed.
DELETE FROM settings WHERE key LIKE '%\_new\_no\_2fa' OR key LIKE '%\_new\_2fa';
