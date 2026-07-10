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
    /** Self-reference (via the Spring proxy) so bulkReview() invokes review() through the transactional proxy,
     *  giving each submission its own transaction — one failure can't roll back the whole batch. */
    private final org.springframework.beans.factory.ObjectProvider<SellerSubmissionService> self;

    /** Admin-defined price per provider+type. Key = "<prefix>.<provider>_<type>" e.g. price.gmail_old, sell.outlook_new. */
    private BigDecimal priceFor(String prefix, SellerSubmission.Provider provider, SellerSubmission.AccountType type, BigDecimal fallback) {
        if (type == null) return fallback;
        String prov = (provider == null ? SellerSubmission.Provider.GMAIL : provider).name().toLowerCase();
        String key = prefix + "." + prov + "_" + type.name().toLowerCase();
        return settings.findById(key)
                .map(s -> { try { return new BigDecimal(s.getValue()); } catch (RuntimeException e) { return fallback; } })
                .orElse(fallback);
    }

    @Transactional
    public SellerSubmission submit(Long sellerId, SubmissionCreateRequest req) {
        SellerSubmission s = repo.save(toEntity(sellerId, req));
        notifications.notifyAdmins("NEW_SUBMISSION", "New seller submission",
                "Submission #" + s.getId() + " — " + s.getTitle());
        return s;
    }

    @Transactional
    public java.util.List<SellerSubmission> submitBulk(Long sellerId, java.util.List<SubmissionCreateRequest> items) {
        java.util.List<SellerSubmission> saved = new java.util.ArrayList<>();
        for (SubmissionCreateRequest req : items) saved.add(repo.save(toEntity(sellerId, req)));
        notifications.notifyAdmins("NEW_SUBMISSION", "New seller submissions",
                saved.size() + " account(s) submitted by seller #" + sellerId);
        return saved;
    }

    private SellerSubmission toEntity(Long sellerId, SubmissionCreateRequest req) {
        String title = (req.title() != null && !req.title().isBlank()) ? req.title() : req.emailAddress();
        String category = (req.category() != null && !req.category().isBlank()) ? req.category() : "Gmail";
        return SellerSubmission.builder()
                .sellerId(sellerId).title(title).category(category)
                .provider(req.provider() == null ? SellerSubmission.Provider.GMAIL : req.provider())
                .description(req.description())
                .askingPrice(req.askingPrice() == null ? BigDecimal.ZERO : req.askingPrice())
                .warrantyDays(req.warrantyDays() == null ? 0 : req.warrantyDays())
                .supportingFiles(req.supportingFiles()).notes(req.notes())
                .emailAddress(req.emailAddress()).emailPassword(req.emailPassword())
                .twoFactorCode(req.twoFactorCode()).accountType(req.accountType())
                .country(req.country()).recoveryEmail(req.recoveryEmail())
                .phoneNumber(req.phoneNumber()).accountCreationYear(req.accountCreationYear())
                .phoneVerified(Boolean.TRUE.equals(req.phoneVerified()))
                .recoveryEmailAdded(Boolean.TRUE.equals(req.recoveryEmailAdded()))
                .quantity(req.quantity() == null ? 1 : req.quantity())
                .additionalInfo(req.additionalInfo())
                .build();
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
        if (s.getStatus() == SellerSubmission.Status.PURCHASED)
            throw ApiException.badRequest("Already purchased");

        switch (req.action()) {
            case "APPROVE" -> {
                // Default to the admin-defined price for this Gmail type when no explicit price is given.
                BigDecimal typePayout = priceFor("price", s.getProvider(), s.getAccountType(), null);
                BigDecimal purchase = req.purchasePrice() != null ? req.purchasePrice()
                        : (s.getCounterPrice() != null ? s.getCounterPrice()
                        : (typePayout != null ? typePayout : s.getAskingPrice()));
                BigDecimal selling = req.sellingPrice() != null ? req.sellingPrice()
                        : priceFor("sell", s.getProvider(), s.getAccountType(), purchase.multiply(new BigDecimal("1.20")));
                s.setStatus(SellerSubmission.Status.PURCHASED);
                // Deliver the seller's actual login details to the buyer: reuse the entity's credential
                // block (email/password/2FA/recovery/…) and append any admin-typed extras.
                String creds = s.buildCredentialPayload();
                String extra = req.deliveryPayload();
                String payload = (extra != null && !extra.isBlank())
                        ? (creds.isBlank() ? extra.trim() : creds + "\n---\n" + extra.trim())
                        : (creds.isBlank() ? null : creds);
                InventoryItem item = inventory.addFromSubmission(s, purchase, selling, payload, req.internalNotes());
                wallet.creditSale(s.getSellerId(), purchase, "submission:" + s.getId() + ":item:" + item.getId());
                notifications.notify(s.getSellerId(), "SUBMISSION_APPROVED",
                        "Submission approved", "Your submission #" + s.getId() + " has been purchased for " + purchase);
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
        s.setTitle((req.title() != null && !req.title().isBlank()) ? req.title() : req.emailAddress());
        s.setCategory((req.category() != null && !req.category().isBlank()) ? req.category() : "Gmail");
        s.setDescription(req.description());
        s.setProvider(req.provider() == null ? SellerSubmission.Provider.GMAIL : req.provider());
        s.setWarrantyDays(req.warrantyDays() == null ? 0 : req.warrantyDays());
        s.setNotes(req.notes());
        s.setEmailAddress(req.emailAddress());
        s.setEmailPassword(req.emailPassword());
        s.setTwoFactorCode(req.twoFactorCode());
        s.setAccountType(req.accountType());
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
