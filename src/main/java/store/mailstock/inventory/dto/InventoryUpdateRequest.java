package store.mailstock.inventory.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record InventoryUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 50) String category,
        @Size(max = 5000) String description,
        @DecimalMin("0.01") BigDecimal sellingPrice,
        Boolean useCategoryPrice,   // true = clear the per-item override so the item follows sell.<provider>_<category>
        @Min(0) Integer warrantyDays,
        @Size(max = 10000) String deliveryPayload,
        @Size(max = 2000) String internalNotes,
        String stockStatus
) {}
