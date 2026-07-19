package store.mailstock.submission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import store.mailstock.common.exception.ApiException;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.inventory.service.InventoryService;
import store.mailstock.notification.service.NotificationService;
import store.mailstock.setting.repo.SettingRepository;
import store.mailstock.submission.dto.*;
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.submission.repo.SellerSubmissionRepository;
import store.mailstock.wallet.service.WalletService;

@Service
@RequiredArgsConstructor
public class SellerSubmissionService {

    private final SellerSubmissionRepository repo;
    private final InventoryService inventory;
    private final WalletService wallet;
    private final NotificationService notifications;
    private final SettingRepository settings;
    private final store.mailstock.telegram.TelegramNotifier telegramNotifier;
    private final store.mailstock.inventory.service.PricingService pricing;
    /** Self-reference (via the Spring proxy) so bulkReview() invokes review() through the transactional proxy,
     *  giving each submission its own transaction — one failure can't roll back the whole batch. */
    private final org.springframework.beans.factory.ObjectProvider<SellerSubmissionService> self;

    /** Admin-defined price per provider+type. Key = "<prefix>.<provider>_<type>" e.g. price.gmail_old, sell.outlook_new. */
    private BigDecimal priceFor(String prefix, SellerSubmission.Provider provider, store.mailstock.submission.entity.AccountCategory category, BigDecimal fallback) {
        if (category == null) return fallback;
        String prov = (provider == null ? SellerSubmission.Provider.GMAIL : provider).name().toLowerCase();
        String key = prefix + "." + prov + "_" + category.key();
        return settings.findById(key)
                .map(s -> { try { return new BigDecimal(s.getValue()); } catch (RuntimeException e) { return fallback; } })
                .orElse(fallback);
    }

    @Transactional
    public SellerSubmission submit(Long sellerId, SubmissionCreateRequest req) {
        requireNotDuplicate(sellerId, req.emailAddress());
        SellerSubmission s = repo.save(toEntity(sellerId, req));
        notifications.notifyAdmins("NEW_SUBMISSION", "New seller submission",
                "Submission #" + s.getId() + " — " + s.getTitle());
        telegramNotifier.adminAlert("new_submission", "New seller submission",
                "Submission #" + s.getId() + " — " + s.getTitle()
                        + (s.getEmailAddress() == null ? "" : "\n" + s.getEmailAddress()));
        return s;
    }

    @Transactional
    public java.util.List<SellerSubmission> submitBulk(Long sellerId, java.util.List<SubmissionCreateRequest> items) {
        java.util.List<SellerSubmission> saved = new java.util.ArrayList<>();
        java.util.Set<String> seenInBatch = new java.util.HashSet<>();
        for (SubmissionCreateRequest req : items) {
            String email = req.emailAddress() == null ? null : req.emailAddress().trim().toLowerCase();
            if (email != null && !email.isBlank() && !seenInBatch.add(email))
                throw ApiException.badRequest("Duplicate email in this batch: " + req.emailAddress());
            requireNotDuplicate(sellerId, req.emailAddress());
            saved.add(repo.save(toEntity(sellerId, req)));
        }
        notifications.notifyAdmins("NEW_SUBMISSION", "New seller submissions",
                saved.size() + " account(s) submitted by seller #" + sellerId);
        telegramNotifier.adminAlert("new_submission", "New seller submissions",
                saved.size() + " account(s) submitted by seller #" + sellerId);
        return saved;
    }

    /**
     * A seller may not submit the same email twice. If they already have a submission for this address
     * (in ANY status — pending, rejected, approved/purchased…), the new one is refused with a message
     * that says what happened to the earlier one.
     */
    private void requireNotDuplicate(Long sellerId, String email) {
        if (email == null || email.isBlank()) return;
        repo.findBySellerIdAndEmailAddressIgnoreCase(sellerId, email.trim()).stream().findFirst().ifPresent(prev -> {
            throw ApiException.conflict("You already submitted this email (" + email.trim()
                    + ") — submission #" + prev.getId() + " is " + prev.getStatus() + ". Duplicate accounts aren't allowed.");
        });
    }

    /** Read-only pre-submit check the seller UI calls: reports whether this email was already submitted. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> checkDuplicate(Long sellerId, String email) {
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        if (email == null || email.isBlank()) { out.put("duplicate", false); return out; }
        var prev = repo.findBySellerIdAndEmailAddressIgnoreCase(sellerId, email.trim()).stream().findFirst();
        out.put("duplicate", prev.isPresent());
        prev.ifPresent(p -> { out.put("status", p.getStatus().name()); out.put("submissionId", p.getId()); });
        return out;
    }

    private SellerSubmission toEntity(Long sellerId, SubmissionCreateRequest req) {
        store.mailstock.submission.entity.AccountCategory category = requireValidCategory(req);
        String title = (req.title() != null && !req.title().isBlank()) ? req.title() : req.emailAddress();
        String cat = (req.category() != null && !req.category().isBlank()) ? req.category() : "Gmail";
        return SellerSubmission.builder()
                .sellerId(sellerId).title(title).category(cat)
                .provider(req.provider() == null ? SellerSubmission.Provider.GMAIL : req.provider())
                .description(req.description())
                .askingPrice(req.askingPrice() == null ? BigDecimal.ZERO : req.askingPrice())
                .warrantyDays(req.warrantyDays() == null ? 0 : req.warrantyDays())
                .supportingFiles(req.supportingFiles()).notes(req.notes())
                .emailAddress(req.emailAddress()).emailPassword(req.emailPassword())
                .twoFactorCode(req.twoFactorCode()).backupCodes(req.backupCodes())
                .accountCategory(category)
                // Keep the legacy OLD/NEW type populated (derived from the category) so warranty
                // auto-replacement and older per-type reports keep working.
                .accountType(category.legacyType)
                .country(req.country()).recoveryEmail(req.recoveryEmail())
                .phoneNumber(req.phoneNumber()).accountCreationYear(req.accountCreationYear())
                .phoneVerified(Boolean.TRUE.equals(req.phoneVerified()))
                .recoveryEmailAdded(Boolean.TRUE.equals(req.recoveryEmailAdded()))
                .quantity(req.quantity() == null ? 1 : req.quantity())
                .additionalInfo(req.additionalInfo())
                .build();
    }

    /** A category is mandatory, 2FA categories require a non-blank 2FA/TOTP secret, and the category
     *  must currently be buyable (a positive payout price is configured for this provider × category —
     *  unpriced categories aren't being purchased right now). */
    private store.mailstock.submission.entity.AccountCategory requireValidCategory(SubmissionCreateRequest req) {
        store.mailstock.submission.entity.AccountCategory category = req.accountCategory();
        if (category == null) throw ApiException.badRequest("Account category is required");
        if (category.requires2FA && (req.twoFactorCode() == null || req.twoFactorCode().isBlank()))
            throw ApiException.badRequest("A 2FA/TOTP secret is required for \"" + category.label + "\" accounts");
        SellerSubmission.Provider provider = req.provider() == null ? SellerSubmission.Provider.GMAIL : req.provider();
        if (!pricing.isBuyable(provider, category))
            throw ApiException.badRequest("We're not buying \"" + category.label + "\" (" + provider
                    + ") accounts right now — this category isn't priced. Please pick another category.");
        return category;
    }

    @Transactional(readOnly = true)
    public Page<SellerSubmission> mine(Long sellerId, Pageable p) {
        return repo.findBySellerIdOrderByIdDesc(sellerId, p);
    }

    @Transactional(readOnly = true)
    public Page<SellerSubmission> adminList(SellerSubmission.Status status, String q, Pageable p) {
        return repo.search(status, null, null, (q != null && !q.isBlank()) ? q.trim() : null, p);
    }

    @Transactional(readOnly = true)
    public SellerSubmission get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Submission not found"));
    }

    /**
     * Fetch a submission for a specific viewer. Non-admins may only read their OWN submissions —
     * without this check any seller could enumerate ids and read another seller's plaintext
     * account credentials (email/password/2FA/recovery).
     */
    @Transactional(readOnly = true)
    public SellerSubmission getForViewer(Long viewerId, boolean isAdmin, Long id) {
        SellerSubmission s = get(id);
        if (!isAdmin && !s.getSellerId().equals(viewerId))
            throw ApiException.forbidden("Not your submission");
        return s;
    }

    @Transactional
    public SellerSubmission review(Long adminId, Long id, SubmissionReviewRequest req) {
        SellerSubmission s = get(id);
        if (s.getStatus() == SellerSubmission.Status.PURCHASED || s.getStatus() == SellerSubmission.Status.APPROVED)
            throw ApiException.badRequest("Already approved");

        switch (req.action()) {
            case "APPROVE" -> {
                // Default to the admin-defined price for this provider + category when no explicit price is given.
                BigDecimal typePayout = priceFor("price", s.getProvider(), s.getAccountCategory(), null);
                BigDecimal purchase = req.purchasePrice() != null ? req.purchasePrice()
                        : (s.getCounterPrice() != null ? s.getCounterPrice()
                        : (typePayout != null ? typePayout : s.getAskingPrice()));
                BigDecimal selling = req.sellingPrice() != null ? req.sellingPrice()
                        : priceFor("sell", s.getProvider(), s.getAccountCategory(), purchase.multiply(new BigDecimal("1.20")));
                // Build the buyer delivery block now (seller creds + any admin extras) and HOLD it on the
                // submission. Approving buys the account from the seller and pays them; it does NOT list it
                // for sale — that is the separate "add to inventory" step below.
                String creds = s.buildCredentialPayload();
                String extra = req.deliveryPayload();
                String payload = (extra != null && !extra.isBlank())
                        ? (creds.isBlank() ? extra.trim() : creds + "\n---\n" + extra.trim())
                        : (creds.isBlank() ? null : creds);
                s.setStatus(SellerSubmission.Status.APPROVED);
                s.setPurchasePrice(purchase);
                s.setSellingPrice(selling);
                s.setDeliveryPayload(payload);
                s.setInternalNotes(req.internalNotes());
                wallet.creditSale(s.getSellerId(), purchase, "submission:" + s.getId());
                notifications.notify(s.getSellerId(), "SUBMISSION_APPROVED",
                        "Submission approved", "Your submission #" + s.getId() + " was approved — " + purchase + " credited to your wallet.");
            }
            case "REJECT" -> {
                s.setStatus(SellerSubmission.Status.REJECTED);
                notifications.notify(s.getSellerId(), "SUBMISSION_REJECTED",
                        "Submission rejected", req.adminNote());
            }
            case "COUNTER" -> {
                if (req.counterPrice() == null) throw ApiException.badRequest("counterPrice required");
                s.setStatus(SellerSubmission.Status.COUNTER_OFFERED);
                s.setCounterPrice(req.counterPrice());
                notifications.notify(s.getSellerId(), "SUBMISSION_COUNTER",
                        "Counter offer received", "Admin offered " + req.counterPrice());
            }
            case "NEEDS_MODIFY" -> {
                s.setStatus(SellerSubmission.Status.NEEDS_MODIFY);
                notifications.notify(s.getSellerId(), "SUBMISSION_NEEDS_MODIFY",
                        "Changes requested",
                        "Admin asked you to fix submission #" + s.getId()
                                + (req.reviewTag() != null ? " (" + req.reviewTag() + ")" : "")
                                + ". " + (req.adminNote() != null ? req.adminNote() : ""));
            }
            default -> throw ApiException.badRequest("Unknown action");
        }
        s.setReviewTag(req.reviewTag());
        s.setAdminNote(req.adminNote());
        s.setReviewedBy(adminId);
        s.setReviewedAt(Instant.now());
        // Admin has full override: acting on an account releases any reviewer's in-progress claim.
        s.setClaimedBy(null);
        s.setClaimedAt(null);
        return repo.save(s);
    }

    /**
     * Second, separate step after APPROVE: turn an APPROVED submission into a sellable inventory item.
     * Only APPROVED submissions can be inventoried, and only once it is an inventory item does it become
     * visible to buyers (as an AVAILABLE listing). Idempotent-ish: refuses if already inventoried.
     */
    @Transactional
    public SellerSubmission addToInventory(Long adminId, Long id) {
        SellerSubmission s = get(id);
        if (s.getStatus() != SellerSubmission.Status.APPROVED)
            throw ApiException.badRequest("Only approved submissions can be added to inventory");
        BigDecimal purchase = s.getPurchasePrice() != null ? s.getPurchasePrice() : s.getAskingPrice();
        BigDecimal selling = s.getSellingPrice() != null ? s.getSellingPrice()
                : priceFor("sell", s.getProvider(), s.getAccountCategory(), purchase.multiply(new BigDecimal("1.20")));
        String payload = (s.getDeliveryPayload() != null && !s.getDeliveryPayload().isBlank())
                ? s.getDeliveryPayload() : s.buildCredentialPayload();
        InventoryItem item = inventory.addFromSubmission(s, purchase, selling, payload, s.getInternalNotes());
        s.setStatus(SellerSubmission.Status.PURCHASED);
        s.setInventoryId(item.getId());
        s.setReviewedBy(adminId);
        s.setReviewedAt(Instant.now());
        s = repo.save(s);
        notifications.notifyAdmins("SUBMISSION_INVENTORIED",
                "Listed for sale", "Submission #" + s.getId() + " is now in inventory (item #" + item.getId() + ") and on sale to buyers.");
        return s;
    }

    /** Admin force-releases a reviewer's claim, returning the account to the PENDING queue. */
    @Transactional
    public SellerSubmission adminReleaseClaim(Long id) {
        SellerSubmission s = get(id);
        if (s.getStatus() == SellerSubmission.Status.CHECKING) s.setStatus(SellerSubmission.Status.PENDING);
        s.setClaimedBy(null);
        s.setClaimedAt(null);
        return repo.save(s);
    }

    /**
     * Apply one review action to many submissions. Each submission is reviewed in its own transaction
     * (invoked through the Spring proxy), so a per-item failure (e.g. "Already purchased") is recorded
     * and the rest of the batch still proceeds. Not @Transactional on purpose.
     */
    public BulkReviewResult bulkReview(Long adminId, SubmissionBulkReviewRequest req) {
        SubmissionReviewRequest r = req.toReview();
        java.util.List<Long> succeeded = new java.util.ArrayList<>();
        java.util.List<BulkReviewResult.Failure> failed = new java.util.ArrayList<>();
        for (Long id : req.ids()) {
            try {
                self.getObject().review(adminId, id, r);
                succeeded.add(id);
            } catch (ApiException e) {
                failed.add(new BulkReviewResult.Failure(id, e.getMessage()));
            }
        }
        return new BulkReviewResult(req.ids().size(), succeeded, failed);
    }

    /**
     * Seller edits a submission ONLY when the admin has requested changes (NEEDS_MODIFY); it then
     * goes back for re-review. Once submitted/accepted, the seller cannot alter the account details.
     */
    @Transactional
    public SellerSubmission updateBySeller(Long sellerId, Long id, SubmissionCreateRequest req) {
        SellerSubmission s = get(id);
        if (!s.getSellerId().equals(sellerId)) throw ApiException.forbidden("Not your submission");
        if (s.getStatus() != SellerSubmission.Status.NEEDS_MODIFY)
            throw ApiException.badRequest("This submission can only be edited when the admin requests changes");
        store.mailstock.submission.entity.AccountCategory category = requireValidCategory(req);
        s.setTitle((req.title() != null && !req.title().isBlank()) ? req.title() : req.emailAddress());
        s.setCategory((req.category() != null && !req.category().isBlank()) ? req.category() : "Gmail");
        s.setDescription(req.description());
        s.setProvider(req.provider() == null ? SellerSubmission.Provider.GMAIL : req.provider());
        s.setWarrantyDays(req.warrantyDays() == null ? 0 : req.warrantyDays());
        s.setNotes(req.notes());
        s.setEmailAddress(req.emailAddress());
        s.setEmailPassword(req.emailPassword());
        s.setTwoFactorCode(req.twoFactorCode());
        s.setBackupCodes(req.backupCodes());
        s.setAccountCategory(category);
        s.setAccountType(category.legacyType);
        s.setCountry(req.country());
        s.setRecoveryEmail(req.recoveryEmail());
        s.setPhoneNumber(req.phoneNumber());
        s.setAccountCreationYear(req.accountCreationYear());
        s.setPhoneVerified(Boolean.TRUE.equals(req.phoneVerified()));
        s.setRecoveryEmailAdded(Boolean.TRUE.equals(req.recoveryEmailAdded()));
        s.setAdditionalInfo(req.additionalInfo());
        s.setStatus(SellerSubmission.Status.PENDING);
        s.setReviewTag(null);
        s = repo.save(s);
        notifications.notifyAdmins("SUBMISSION_RESUBMITTED",
                "Submission resubmitted", "Submission #" + s.getId() + " was updated and needs re-review");
        return s;
    }

    // ----------------------------------------------------------------------------------
    // Reviewer flow: a REVIEWER claims a pending account, test-logs-in to verify it, then
    // accepts (-> ACCEPTED, ready for admin pricing) or rejects it. Claiming locks the
    // account so no two reviewers check the same one.
    // ----------------------------------------------------------------------------------

    /** A claim older than this is considered abandoned and may be re-claimed by another reviewer. */
    private static final java.time.Duration CLAIM_TTL = java.time.Duration.ofMinutes(30);

    /** Accounts up for review (PENDING) or currently being checked (CHECKING), newest first. */
    @Transactional(readOnly = true)
    public java.util.List<SellerSubmission> reviewerQueue() {
        return repo.findByStatusInOrderByIdDesc(
                java.util.List.of(SellerSubmission.Status.PENDING, SellerSubmission.Status.CHECKING));
    }

    /** Reviewer may read full credentials only for a PENDING account or one they themselves claimed. */
    @Transactional(readOnly = true)
    public SellerSubmission getForReviewer(Long reviewerId, Long id) {
        SellerSubmission s = get(id);
        if (s.getStatus() == SellerSubmission.Status.CHECKING && !reviewerId.equals(s.getClaimedBy()))
            throw ApiException.forbidden("This account is being reviewed by someone else");
        if (s.getStatus() != SellerSubmission.Status.PENDING
                && s.getStatus() != SellerSubmission.Status.CHECKING)
            throw ApiException.badRequest("This account is no longer open for review");
        return s;
    }

    /**
     * Atomically claim a PENDING (or stale-CHECKING) account for this reviewer. Throws 409-style
     * conflict if another reviewer got there first. The DB conditional UPDATE is the source of truth.
     */
    @Transactional
    public SellerSubmission claimForReview(Long reviewerId, Long id) {
        get(id); // 404 if missing
        Instant now = Instant.now();
        int updated = repo.claim(id, reviewerId, now, now.minus(CLAIM_TTL),
                SellerSubmission.Status.PENDING, SellerSubmission.Status.CHECKING);
        if (updated == 0) {
            SellerSubmission cur = get(id);
            if (reviewerId.equals(cur.getClaimedBy())) return cur; // already mine — idempotent
            throw ApiException.conflict("This account is already being reviewed by someone else");
        }
        return get(id);
    }

    /** Release a claim the reviewer holds, putting the account back in the PENDING queue. */
    @Transactional
    public SellerSubmission releaseClaim(Long reviewerId, Long id) {
        SellerSubmission s = requireMyClaim(reviewerId, id);
        s.setStatus(SellerSubmission.Status.PENDING);
        s.setClaimedBy(null);
        s.setClaimedAt(null);
        return repo.save(s);
    }

    /** Reviewer verified the credentials work → mark ACCEPTED so an admin can price & list it. */
    @Transactional
    public SellerSubmission reviewerAccept(Long reviewerId, Long id) {
        SellerSubmission s = requireMyClaim(reviewerId, id);
        s.setStatus(SellerSubmission.Status.ACCEPTED);
        s.setReviewedBy(reviewerId);
        s.setReviewedAt(Instant.now());
        s.setClaimedBy(null);
        s.setClaimedAt(null);
        s = repo.save(s);
        notifications.notifyAdmins("SUBMISSION_ACCEPTED",
                "Account verified by reviewer",
                "Submission #" + s.getId() + " passed review and is ready to price & list");
        return s;
    }

    /** Reviewer could not verify the account → reject it with a reason. */
    @Transactional
    public SellerSubmission reviewerReject(Long reviewerId, Long id, String reviewTag, String note) {
        SellerSubmission s = requireMyClaim(reviewerId, id);
        s.setStatus(SellerSubmission.Status.REJECTED);
        s.setReviewTag(reviewTag);
        s.setAdminNote(note);
        s.setReviewedBy(reviewerId);
        s.setReviewedAt(Instant.now());
        s.setClaimedBy(null);
        s.setClaimedAt(null);
        s = repo.save(s);
        notifications.notify(s.getSellerId(), "SUBMISSION_REJECTED",
                "Submission rejected", note);
        return s;
    }

    /** Guard: the action is only valid on an account this reviewer currently holds a CHECKING claim on. */
    private SellerSubmission requireMyClaim(Long reviewerId, Long id) {
        SellerSubmission s = get(id);
        if (s.getStatus() != SellerSubmission.Status.CHECKING || !reviewerId.equals(s.getClaimedBy()))
            throw ApiException.badRequest("You are not currently reviewing this account");
        return s;
    }

    @Transactional
    public SellerSubmission sellerRespondToCounter(Long sellerId, Long id, boolean accept) {
        SellerSubmission s = get(id);
        if (!s.getSellerId().equals(sellerId)) throw ApiException.forbidden("Not your submission");
        if (s.getStatus() != SellerSubmission.Status.COUNTER_OFFERED)
            throw ApiException.badRequest("No counter offer to respond to");
        if (accept) {
            s.setAskingPrice(s.getCounterPrice());
            s.setStatus(SellerSubmission.Status.ACCEPTED);
            notifications.notifyAdmins("COUNTER_ACCEPTED",
                    "Seller accepted counter", "Submission #" + s.getId());
        } else {
            s.setStatus(SellerSubmission.Status.REJECTED);
        }
        return repo.save(s);
    }
}
