package store.mailstock.warranty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarrantyDecisionRequest(
        @NotBlank @Pattern(regexp = "REPLACE|REFUND|REJECT|CLOSE") String resolution,
        // Seller payout clawback for a dead account (share of purchase price). null/blank => NONE.
        @Pattern(regexp = "NONE|FULL|HALF|QUARTER") String sellerClawback,
        @Size(max = 2000) String adminNote
) {}
