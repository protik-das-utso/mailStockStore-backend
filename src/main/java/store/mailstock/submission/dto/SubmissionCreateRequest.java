package store.mailstock.submission.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

import store.mailstock.submission.entity.AccountCategory;
import store.mailstock.submission.entity.SellerSubmission.AccountType;
import store.mailstock.submission.entity.SellerSubmission.Provider;

public record SubmissionCreateRequest(
        // Listing meta — auto-derived from the email address / category when left blank
        @Size(max = 200) String title,
        @Size(max = 50) String category,
        @Size(max = 5000) String description,
        @DecimalMin("0.00") BigDecimal askingPrice,
        @Min(0) @Max(3650) Integer warrantyDays,
        @Size(max = 2000) String supportingFiles,
        @Size(max = 2000) String notes,

        // --- Email account details (core) ---
        Provider provider,                              // GMAIL (default) or OUTLOOK
        @NotBlank @Email @Size(max = 255) String emailAddress,
        @NotBlank @Size(max = 255) String emailPassword,
        @Size(max = 120) String twoFactorCode,          // required when the category has 2FA (checked in service)
        @Size(max = 5000) String backupCodes,           // optional one-time backup codes
        AccountType accountType,                         // legacy; derived from category when omitted
        @NotNull AccountCategory accountCategory,        // buyer-facing age + 2FA taxonomy
        @NotBlank @Size(max = 80) String country,

        // --- Advanced options ---
        @Email @Size(max = 255) String recoveryEmail,
        @Size(max = 30) String phoneNumber,
        @Min(2004) @Max(2100) Integer accountCreationYear,
        Boolean phoneVerified,
        Boolean recoveryEmailAdded,
        @Min(1) @Max(1000) Integer quantity,
        @Size(max = 5000) String additionalInfo
) {}
