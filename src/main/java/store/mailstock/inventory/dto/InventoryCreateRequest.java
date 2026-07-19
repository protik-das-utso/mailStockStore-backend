package store.mailstock.inventory.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

import store.mailstock.submission.entity.AccountCategory;
import store.mailstock.submission.entity.SellerSubmission.Provider;

/**
 * Admin adds an account directly to inventory (no seller submission): the item is created
 * AVAILABLE and immediately on sale. Credentials are assembled into the buyer delivery payload
 * server-side, mirroring the submission → inventory path.
 */
public record InventoryCreateRequest(
        Provider provider,                               // GMAIL (default) or OUTLOOK
        @NotBlank @Email @Size(max = 255) String emailAddress,
        @NotBlank @Size(max = 255) String emailPassword,
        @Size(max = 120) String twoFactorCode,           // required when the category has 2FA (checked in service)
        @Size(max = 5000) String backupCodes,
        @NotNull AccountCategory accountCategory,
        @Size(max = 80) String country,
        @Min(1990) @Max(2100) Integer accountCreationYear,   // e.g. 2015 (optional)
        @Email @Size(max = 255) String recoveryEmail,

        @Size(max = 200) String title,                   // defaults to the email address
        @Size(max = 5000) String description,
        @DecimalMin("0.00") BigDecimal purchasePrice,    // what the account cost us; defaults to 0
        @DecimalMin("0.01") BigDecimal sellingPrice,     // NULL = follow sell.<provider>_<category>; explicit value locks it
        @Size(max = 2000) String internalNotes
) {}
