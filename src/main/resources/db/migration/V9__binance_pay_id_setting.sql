-- Binance Pay receiver ID shown to buyers alongside the "Scan to pay" QR (edit in Admin -> Settings).
INSERT INTO settings (key, value) VALUES
  ('deposit.binance_pay_id', '1207429984')
ON CONFLICT (key) DO NOTHING;
