package store.mailstock.wallet.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record WithdrawCreateRequest(
        @NotNull @DecimalMin("1.00") BigDecimal amount,
        @NotBlank @Size(max = 200) String destination
) {}
