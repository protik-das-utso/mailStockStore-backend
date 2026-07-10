package store.mailstock.submission.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record SubmissionReviewRequest(
        @NotBlank @Pattern(regexp = "APPROVE|REJECT|COUNTER|NEEDS_MODIFY") String action,
        @DecimalMin("0.01") BigDecimal counterPrice,
        @DecimalMin("0.01") BigDecimal purchasePrice,   // used on APPROVE / ACCEPT_OFFER
        @DecimalMin("0.01") BigDecimal sellingPrice,    // for inventory add on APPROVE
        @Size(max = 40) String reviewTag,               // structured verdict e.g. PASSWORD_DEAD
        @Size(max = 2000) String adminNote,
        @Size(max = 10000) String deliveryPayload,
        @Size(max = 2000) String internalNotes
) {}
