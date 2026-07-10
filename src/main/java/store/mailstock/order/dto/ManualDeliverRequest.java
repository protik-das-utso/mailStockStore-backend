package store.mailstock.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualDeliverRequest(
        @NotNull Long userId,
        @NotNull Long inventoryId,
        boolean chargeBalance,
        @Size(max = 200) String note
) {}
