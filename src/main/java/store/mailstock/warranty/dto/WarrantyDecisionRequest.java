package store.mailstock.warranty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarrantyDecisionRequest(
        @NotBlank @Pattern(regexp = "REPLACE|REFUND|REJECT|CLOSE") String resolution,
        // Seller payout clawback for a dead account (share of purchase price). null/blank => NONE.
        @Pattern(regexp = "NONE|FULL|HALF|QUARTER") String sellerClawback,
        // Specific available inventory item to hand over on REPLACE. null => auto-pick same provider+category.
        Long replacementInventoryId,
        @Size(max = 2000) String adminNote
) {}
