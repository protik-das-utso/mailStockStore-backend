package store.mailstock.warranty.dto;

import jakarta.validation.constraints.*;

public record WarrantyCreateRequest(
        @NotNull Long orderItemId,
        @NotBlank @Size(max = 60) String reason,
        @Size(max = 5000) String description,
        // http(s) only — this is rendered as a raw <a href> to admins reviewing the claim; anything
        // else (e.g. a javascript: URI) would execute in the admin's session when clicked.
        @Size(max = 2000) @Pattern(regexp = "^https?://.+", message = "Must be a valid http(s) URL") String evidenceUrl
) {}
