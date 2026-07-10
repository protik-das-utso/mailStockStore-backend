-- Structured review verdict tag the admin sets when reviewing a submission (e.g. PASSWORD_DEAD)
ALTER TABLE seller_submissions
    ADD COLUMN review_tag VARCHAR(40);
