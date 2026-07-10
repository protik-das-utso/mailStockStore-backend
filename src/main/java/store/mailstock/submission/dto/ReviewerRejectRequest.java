package store.mailstock.submission.dto;

import jakarta.validation.constraints.Size;

/** Reviewer's reason for rejecting an account they could not verify. */
public record ReviewerRejectRequest(
        @Size(max = 40) String reviewTag,     // structured verdict e.g. PASSWORD_DEAD, TWO_FA_REQUIRED
        @Size(max = 2000) String note         // shown to the seller
) {}
