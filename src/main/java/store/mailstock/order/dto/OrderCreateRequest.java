package store.mailstock.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateRequest(
        @NotEmpty List<Long> inventoryIds,
        @Size(max = 60) String couponCode
) {}
