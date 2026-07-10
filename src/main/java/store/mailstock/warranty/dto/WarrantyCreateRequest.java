package store.mailstock.warranty.dto;

import jakarta.validation.constraints.*;

public record WarrantyCreateRequest(
        @NotNull Long orderItemId,
        @NotBlank @Size(max = 60) String reason,
        @Size(max = 5000) String description,
        @Size(max = 2000) String evidenceUrl
) {}
