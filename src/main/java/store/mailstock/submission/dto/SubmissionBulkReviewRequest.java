package store.mailstock.submission.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Apply one review action to many submissions at once. Prices are optional — when omitted on APPROVE,
 * each submission falls back to its admin-defined type payout / counter / asking price (see review()).
 */
public record SubmissionBulkReviewRequest(
        @NotEmpty List<Long> ids,
        @NotBlank @Pattern(regexp = "APPROVE|REJECT|COUNTER|NEEDS_MODIFY") String action,
        @DecimalMin("0.01") BigDecimal counterPrice,
        @DecimalMin("0.01") BigDecimal purchasePrice,
        @DecimalMin("0.01") BigDecimal sellingPrice,
        @Size(max = 40) String reviewTag,
        @Size(max = 2000) String adminNote
) {
    /** Translate to the single-submission review request (per-item delivery/internal notes not supported in bulk). */
    public SubmissionReviewRequest toReview() {
        return new SubmissionReviewRequest(action, counterPrice, purchasePrice, sellingPrice, reviewTag, adminNote, null, null);
    }
}
