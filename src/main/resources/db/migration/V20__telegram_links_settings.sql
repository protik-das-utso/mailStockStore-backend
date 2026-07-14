-- Telegram channel + bot links shown in the site header. Stored as settings (not hardcoded in the
-- frontend) so an admin can change them without a rebuild — the frontend is a static export, so a
-- hardcoded link would need a full rebuild + re-upload to change.
--
-- They carry no "private" prefix (sell./warranty./stock.target_/abuse./email.), so SettingController
-- exposes them on /api/public/settings, and Admin -> Settings -> General renders them automatically.
INSERT INTO settings (key, value) VALUES
  ('site.telegram_channel', 'https://t.me/mailstockstore'),
  ('site.telegram_bot',     'https://t.me/mailstockstorebot')
ON CONFLICT (key) DO NOTHING;
