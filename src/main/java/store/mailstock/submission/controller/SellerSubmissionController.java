package store.mailstock.submission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.dto.PageResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.submission.dto.*;
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.submission.service.SellerSubmissionService;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SellerSubmissionController {

    private final SellerSubmissionService svc;

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<SellerSubmission> submit(@Valid @RequestBody SubmissionCreateRequest req) {
        return ApiResponse.ok(svc.submit(SecurityUtils.currentUserId(), req));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<java.util.List<SellerSubmission>> submitBulk(@Valid @RequestBody SubmissionBulkRequest req) {
        return ApiResponse.ok(svc.submitBulk(SecurityUtils.currentUserId(), req.items()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<PageResponse<SellerSubmission>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(svc.mine(SecurityUtils.currentUserId(), PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
    public ApiResponse<SellerSubmission> get(@PathVariable Long id) {
        var u = SecurityUtils.currentUser();
        boolean admin = u.hasRole(store.mailstock.auth.entity.Role.ADMIN);
        return ApiResponse.ok(svc.getForViewer(u.getId(), admin, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<SellerSubmission> update(@PathVariable Long id, @Valid @RequestBody SubmissionCreateRequest req) {
        return ApiResponse.ok(svc.updateBySeller(SecurityUtils.currentUserId(), id, req));
    }

    @PostMapping("/{id}/counter-response")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<SellerSubmission> counter(@PathVariable Long id, @Valid @RequestBody SubmissionCounterAcceptRequest req) {
        return ApiResponse.ok(svc.sellerRespondToCounter(SecurityUtils.currentUserId(), id, req.accept()));
    }

    // Admin
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<SellerSubmission>> adminList(
            @RequestParam(required = false) SellerSubmission.Status status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(svc.adminList(status, q, PageRequest.of(page, size))));
    }

    @PostMapping("/admin/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SellerSubmission> review(@PathVariable Long id, @Valid @RequestBody SubmissionReviewRequest req) {
        return ApiResponse.ok(svc.review(SecurityUtils.currentUserId(), id, req));
    }

    /** Apply one review action (approve/reject/counter/needs-modify) to many submissions at once. */
    @PostMapping("/admin/bulk-review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BulkReviewResult> bulkReview(@Valid @RequestBody SubmissionBulkReviewRequest req) {
        return ApiResponse.ok(svc.bulkReview(SecurityUtils.currentUserId(), req));
    }

    /** Admin override: force-release a reviewer's in-progress claim back to the PENDING queue. */
    @PostMapping("/admin/{id}/release-claim")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SellerSubmission> adminReleaseClaim(@PathVariable Long id) {
        return ApiResponse.ok(svc.adminReleaseClaim(id));
    }
}
