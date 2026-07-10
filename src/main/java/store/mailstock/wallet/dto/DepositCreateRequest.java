package store.mailstock.wallet.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record DepositCreateRequest(
        @NotNull @DecimalMin("1.00") BigDecimal amount,
        @Size(max = 200) String txid
) {}
