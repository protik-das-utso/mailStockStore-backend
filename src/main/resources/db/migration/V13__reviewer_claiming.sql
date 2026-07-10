-- Reviewer role claiming: a reviewer "claims" a PENDING submission (status -> CHECKING) so no
-- other reviewer can check the same account at the same time. Claiming is done via an atomic
-- conditional UPDATE (see SellerSubmissionRepository.claim), so these columns just record who
-- holds the claim and when it was taken (used for stale-claim expiry).
ALTER TABLE seller_submissions
    ADD COLUMN claimed_by BIGINT,
    ADD COLUMN claimed_at TIMESTAMPTZ;

-- Speeds up the reviewer queue (PENDING + CHECKING, ordered) and claim lookups.
CREATE INDEX idx_submission_status_claimed ON seller_submissions (status, claimed_by);
