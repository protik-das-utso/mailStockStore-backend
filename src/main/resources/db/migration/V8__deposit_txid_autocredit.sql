-- Automated Binance deposit crediting.
-- A single on-chain transaction (txid) may be credited at most once across the whole system:
-- the partial unique index blocks two APPROVED deposits from ever sharing a txid, which is the
-- backstop that stops a buyer from pasting someone else's (or a re-used) txid to double-credit.
CREATE UNIQUE INDEX ux_deposit_txid_approved
    ON wallet_deposits (txid)
    WHERE status = 'APPROVED' AND txid IS NOT NULL;

-- Fast lookup of the (small) set of still-pending deposits the reconciler scans each poll.
CREATE INDEX idx_deposit_status_txid ON wallet_deposits (status, txid);
