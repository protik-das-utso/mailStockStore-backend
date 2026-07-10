package store.mailstock.submission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.submission.dto.ReviewerRejectRequest;
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.submission.service.SellerSubmissionService;

/**
 * Reviewer panel: a REVIEWER (or ADMIN) claims a pending account, test-logs-in to verify the
 * credentials, then accepts (-> ready for admin pricing) or rejects it. Claiming is atomic so
 * two reviewers can never check the same account at once.
 */
@RestController
@RequestMapping("/api/reviewer/submissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
public class ReviewerController {

    private final SellerSubmissionService svc;

    /** Accounts open for review (PENDING) or currently being checked (CHECKING). */
    @GetMapping("/queue")
    public ApiResponse<java.util.List<SellerSubmission>> queue() {
        return ApiResponse.ok(svc.reviewerQueue());
    }

    /** Full details (incl. credentials) for an account this reviewer may check. */
    @GetMapping("/{id}")
    public ApiResponse<SellerSubmission> get(@PathVariable Long id) {
        return ApiResponse.ok(svc.getForReviewer(SecurityUtils.currentUserId(), id));
    }

    /** Claim an account for review (locks out other reviewers). 409 if already taken. */
    @PostMapping("/{id}/claim")
    public ApiResponse<SellerSubmission> claim(@PathVariable Long id) {
        return ApiResponse.ok(svc.claimForReview(SecurityUtils.currentUserId(), id));
    }

    /** Give up a claim so another reviewer can take the account. */
    @PostMapping("/{id}/release")
    public ApiResponse<SellerSubmission> release(@PathVariable Long id) {
        return ApiResponse.ok(svc.releaseClaim(SecurityUtils.currentUserId(), id));
    }

    /** Credentials verified → mark ACCEPTED for the admin to price & list. */
    @PostMapping("/{id}/accept")
    public ApiResponse<SellerSubmission> accept(@PathVariable Long id) {
        return ApiResponse.ok(svc.reviewerAccept(SecurityUtils.currentUserId(), id));
    }

    /** Could not verify → reject with a reason. */
    @PostMapping("/{id}/reject")
    public ApiResponse<SellerSubmission> reject(@PathVariable Long id, @Valid @RequestBody ReviewerRejectRequest req) {
        return ApiResponse.ok(svc.reviewerReject(SecurityUtils.currentUserId(), id, req.reviewTag(), req.note()));
    }
}
