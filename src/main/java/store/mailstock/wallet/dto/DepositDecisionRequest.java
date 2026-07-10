package store.mailstock.wallet.dto;

import jakarta.validation.constraints.Size;

public record DepositDecisionRequest(
        boolean approve,
        @Size(max = 2000) String adminNote
) {}
