-- Lower the minimum withdrawal to $2. V1 seeded this at '10'; the code fallback is now $2 as well.
-- Upsert so it applies whether or not an admin has since edited the value.
INSERT INTO settings (key, value) VALUES ('site.min_withdraw', '2')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
