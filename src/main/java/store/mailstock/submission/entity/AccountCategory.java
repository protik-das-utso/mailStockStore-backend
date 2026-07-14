package store.mailstock.submission.entity;

/**
 * Buyer-facing account taxonomy combining age with 2FA state. Sellers pick one when submitting;
 * buyers see & filter by it. Each category carries a display {@link #label}, whether a working
 * 2FA/TOTP secret is {@link #requires2FA required} on submission, and the legacy {@link #legacyType}
 * (OLD/NEW) kept populated so warranty auto-replacement and older reports keep working.
 *
 * The lower-cased enum name is the settings key suffix, e.g. price.gmail_new_1_3m_no_2fa / sell.outlook_y3_plus.
 */
public enum AccountCategory {
    // Fine-grained "brand new" age buckets (added 2026-07-11). Warranty default follows policy:
    // 0–3 days → 1 day, 4–7 days → 7 days; admin can override per category in the pricing panel.
    NEW_0D_NO_2FA  ("New account · 0 days · no 2FA",         false, SellerSubmission.AccountType.NEW, 1),
    NEW_0D_2FA     ("New account · 0 days · 2FA enabled",    true,  SellerSubmission.AccountType.NEW, 1),
    NEW_1_3D_NO_2FA("New account · 1–3 days · no 2FA",       false, SellerSubmission.AccountType.NEW, 1),
    NEW_1_3D_2FA   ("New account · 1–3 days · 2FA enabled",  true,  SellerSubmission.AccountType.NEW, 1),
    NEW_4_7D_NO_2FA("New account · 4–7 days · no 2FA",       false, SellerSubmission.AccountType.NEW, 7),
    NEW_4_7D_2FA   ("New account · 4–7 days · 2FA enabled",  true,  SellerSubmission.AccountType.NEW, 7),
    NEW_1_3M_NO_2FA("New account · 1–3 months · no 2FA",     false, SellerSubmission.AccountType.NEW, 7),
    NEW_1_3M_2FA   ("New account · 1–3 months · 2FA enabled",true,  SellerSubmission.AccountType.NEW, 7),

    // NEW_NO_2FA / NEW_2FA ("0–3 months") were retired in V21 — they overlapped the buckets above.
    // Existing rows were remapped to NEW_1_3M_*; do not reintroduce these names.
    M3_12_NO_2FA("3–12 months old · no 2FA",                false, SellerSubmission.AccountType.NEW, 7),
    M3_12_2FA   ("3–12 months old · 2FA enabled",           true,  SellerSubmission.AccountType.NEW, 7),
    Y1_3        ("1–3 years old · 2FA enabled",             true,  SellerSubmission.AccountType.OLD, 7),
    Y3_PLUS     ("3+ years old · 2FA enabled",              true,  SellerSubmission.AccountType.OLD, 7);

    public final String label;
    public final boolean requires2FA;
    public final SellerSubmission.AccountType legacyType;
    /** Warranty days granted by default for this category (admin can override via settings). */
    public final int defaultWarrantyDays;

    AccountCategory(String label, boolean requires2FA, SellerSubmission.AccountType legacyType, int defaultWarrantyDays) {
        this.label = label;
        this.requires2FA = requires2FA;
        this.legacyType = legacyType;
        this.defaultWarrantyDays = defaultWarrantyDays;
    }

    /** Settings key suffix, e.g. "new_1_3m_no_2fa" → price.gmail_new_1_3m_no_2fa. */
    public String key() { return name().toLowerCase(); }
}
