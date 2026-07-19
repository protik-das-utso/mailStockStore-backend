-- Admin Telegram alerts: the owner's personal chat id (comma-separated for several) and the
-- per-event on/off switches. Blank chat id = alerts go nowhere until the admin fills it in on
-- the Settings → Telegram tab. Toggles default ON (missing row is also treated as ON in code).
INSERT INTO settings (key, value) VALUES
  ('telegram.admin_chat_id', ''),
  ('telegram.alert.new_user', 'true'),
  ('telegram.alert.new_submission', 'true')
ON CONFLICT (key) DO NOTHING;
