package store.mailstock.email;

/**
 * Every event that can email a user, and whether it does so out of the box. Each one is an admin
 * toggle stored in settings as {@code email.<lower_case_name>} (e.g. email.support_reply) — see
 * {@link EmailPreferenceService}.
 *
 * The {@code type} matches the notification type already passed to NotificationService, so a single
 * lookup covers the in-app notification and its email. Defaults deliberately reproduce the behaviour
 * the site had before toggles existed: support emails on, everything else off (in-app + Telegram
 * only). Turning one on starts sending that email immediately — no redeploy.
 */
public enum EmailEvent {

    // ---- Customer / seller emails ----
    ORDER_DELIVERED        ("Order delivered",              "Buyer's order is paid and the accounts are delivered.",        Audience.CUSTOMER, false),
    MANUAL_DELIVERY        ("Manual delivery",              "Admin hands an account to a buyer directly.",                  Audience.CUSTOMER, false),
    WARRANTY_REPLACED      ("Warranty replacement done",    "A replacement account has been delivered for a claim.",        Audience.CUSTOMER, false),
    WARRANTY_REFUND        ("Warranty refund issued",       "A warranty claim was resolved with a refund.",                 Audience.CUSTOMER, false),
    WARRANTY_UPDATED       ("Warranty claim updated",       "A claim changed status (e.g. rejected or closed).",            Audience.CUSTOMER, false),
    ACCOUNT_DEAD           ("Account reported dead",        "Seller is told one of their sold accounts was marked dead.",   Audience.CUSTOMER, false),
    SUBMISSION_APPROVED    ("Submission approved",          "Seller's submitted account was approved and paid out.",        Audience.CUSTOMER, false),
    SUBMISSION_REJECTED    ("Submission rejected",          "Seller's submitted account was rejected.",                     Audience.CUSTOMER, false),
    SUBMISSION_COUNTER     ("Counter offer made",           "Admin offered a different price for a submission.",            Audience.CUSTOMER, false),
    SUBMISSION_NEEDS_MODIFY("Changes requested",            "Admin asked the seller to fix and resubmit.",                  Audience.CUSTOMER, false),
    WITHDRAW_APPROVED      ("Withdrawal approved",          "Seller's withdrawal was paid out.",                            Audience.CUSTOMER, false),
    WITHDRAW_REJECTED      ("Withdrawal rejected",          "Seller's withdrawal was rejected and refunded.",               Audience.CUSTOMER, false),
    DEPOSIT_APPROVED       ("Deposit approved",             "Buyer's balance deposit was credited.",                        Audience.CUSTOMER, false),
    DEPOSIT_REJECTED       ("Deposit rejected",             "Buyer's balance deposit was rejected.",                        Audience.CUSTOMER, false),
    BALANCE_CREDITED       ("Balance added",                "Admin added balance to a user's wallet.",                      Audience.CUSTOMER, false),
    SUPPORT_REPLY          ("Admin replied to ticket",      "Staff answered the customer's support ticket.",                Audience.CUSTOMER, true),
    SUPPORT_CLOSED         ("Ticket closed",                "A support ticket was closed.",                                 Audience.CUSTOMER, true),
    SUPPORT_REOPENED       ("Ticket reopened",              "A support ticket was reopened.",                               Audience.CUSTOMER, true),
    SUPPORT_STATUS         ("Ticket status changed",        "Admin changed a ticket's status.",                             Audience.CUSTOMER, true),

    // ---- Admin emails ----
    SUPPORT_NEW            ("New support ticket",           "A customer opened a ticket.",                                  Audience.ADMIN, true),
    NEW_SUBMISSION         ("New seller submission",        "A seller submitted an account for review.",                    Audience.ADMIN, false),
    NEW_ORDER              ("New order",                    "A buyer placed an order.",                                     Audience.ADMIN, false),
    NEW_WARRANTY           ("New warranty claim",           "A buyer opened a warranty claim.",                             Audience.ADMIN, false),
    NEW_DEPOSIT            ("New balance deposit",          "A buyer submitted a deposit for verification.",                Audience.ADMIN, false),
    WARRANTY_MANUAL        ("Manual replacement needed",    "No replacement stock was available for a claim.",              Audience.ADMIN, false),
    ABUSE_FLAG             ("Buyer auto-flagged",           "A buyer crossed the abuse threshold and was flagged.",         Audience.ADMIN, false);

    public enum Audience { CUSTOMER, ADMIN }

    public final String label;
    public final String description;
    public final Audience audience;
    /** Whether this email sends when the admin has never touched the toggle. */
    public final boolean defaultOn;

    EmailEvent(String label, String description, Audience audience, boolean defaultOn) {
        this.label = label;
        this.description = description;
        this.audience = audience;
        this.defaultOn = defaultOn;
    }

    /** Settings key holding this toggle, e.g. "email.support_reply". */
    public String settingKey() { return "email." + name().toLowerCase(); }

    /** The notification type this event corresponds to, or null if the type isn't emailable. */
    public static EmailEvent byType(String type) {
        if (type == null) return null;
        try { return valueOf(type.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
