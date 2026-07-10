package store.mailstock.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminCreditRequest(
        @NotNull @Positive BigDecimal amount,
        @Size(max = 200) String note
) {}
