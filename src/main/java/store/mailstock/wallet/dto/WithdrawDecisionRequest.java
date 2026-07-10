package store.mailstock.wallet.dto;

import jakarta.validation.constraints.Size;

public record WithdrawDecisionRequest(
        boolean approve,
        @Size(max = 500) String adminNote,
        @Size(max = 200) String payoutTxid
) {}
