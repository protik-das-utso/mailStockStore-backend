package store.mailstock.submission.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "seller_submissions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class SellerSubmission {
    public enum Status { PENDING, CHECKING, APPROVED, REJECTED, COUNTER_OFFERED, ACCEPTED, PURCHASED, NEEDS_MODIFY }
    public enum AccountType { OLD, NEW }
    public enum Provider { GMAIL, OUTLOOK }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long sellerId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 50) private String category;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal askingPrice;
    @Column(nullable = false) @Builder.Default private Integer warrantyDays = 0;
    @Column(columnDefinition = "TEXT") private String supportingFiles;
    @Column(columnDefinition = "TEXT") private String notes;

    // --- Email account details (core) ---
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) @Builder.Default private Provider provider = Provider.GMAIL;
    @Column(length = 255) private String emailAddress;
    @Column(length = 255) private String emailPassword;
    @Column(length = 120) private String twoFactorCode;          // 2FA / TOTP secret (required for 2FA categories)
    @Column(columnDefinition = "TEXT") private String backupCodes;  // one-time backup/recovery codes (optional)
    @Enumerated(EnumType.STRING) @Column(length = 10) private AccountType accountType;   // legacy OLD/NEW, derived from category
    @Enumerated(EnumType.STRING) @Column(length = 20) private AccountCategory accountCategory;  // buyer-facing age + 2FA taxonomy
    @Column(length = 80) private String country;

    // --- Advanced options ---
    @Column(length = 255) private String recoveryEmail;          // recovery email set on the account
    @Column(length = 30) private String phoneNumber;             // recovery phone (optional)
    private Integer accountCreationYear;                          // e.g. 2015
    @Builder.Default private Boolean phoneVerified = false;
    @Builder.Default private Boolean recoveryEmailAdded = false;  // did the seller add OUR recovery email
    @Builder.Default private Integer quantity = 1;               // bulk submissions
    @Column(columnDefinition = "TEXT") private String additionalInfo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private Status status = Status.PENDING;
    @Column(precision = 14, scale = 2) private BigDecimal counterPrice;
    // --- Approval outcome: the agreed deal, held on the submission until it is pushed to inventory ---
    @Column(precision = 14, scale = 2) private BigDecimal purchasePrice;   // paid to the seller on approval
    @Column(precision = 14, scale = 2) private BigDecimal sellingPrice;    // intended buyer price when listed
    @Column(columnDefinition = "TEXT") private String deliveryPayload;     // credentials block delivered to the buyer
    @Column(columnDefinition = "TEXT") private String internalNotes;       // admin-only notes carried onto the item
    private Long inventoryId;                                              // set once it becomes an inventory item
    @Column(columnDefinition = "TEXT") private String adminNote;
    @Column(length = 40) private String reviewTag;   // structured verdict e.g. PASSWORD_DEAD, TWO_FA_REQUIRED
    private Long reviewedBy;
    private Instant reviewedAt;
    // --- Reviewer claiming (prevents two reviewers checking the same account) ---
    private Long claimedBy;                                       // reviewer currently checking this account
    private Instant claimedAt;                                    // when the claim was taken (used for stale-claim expiry)
    @CreatedDate @Column(nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;

    /** Pre-built credential block admins can copy into the buyer delivery payload after verification. */
    @Transient
    public String buildCredentialPayload() {
        StringBuilder sb = new StringBuilder();
        if (provider != null) sb.append("Provider: ").append(provider).append('\n');
        if (emailAddress != null) sb.append("Email: ").append(emailAddress).append('\n');
        if (emailPassword != null) sb.append("Password: ").append(emailPassword).append('\n');
        if (twoFactorCode != null && !twoFactorCode.isBlank()) sb.append("2FA secret: ").append(twoFactorCode).append('\n');
        if (backupCodes != null && !backupCodes.isBlank()) sb.append("Backup codes: ").append(backupCodes).append('\n');
        if (recoveryEmail != null && !recoveryEmail.isBlank()) sb.append("Recovery email: ").append(recoveryEmail).append('\n');
        if (phoneNumber != null && !phoneNumber.isBlank()) sb.append("Recovery phone: ").append(phoneNumber).append('\n');
        if (country != null && !country.isBlank()) sb.append("Country: ").append(country).append('\n');
        if (accountCategory != null) sb.append("Category: ").append(accountCategory.label).append('\n');
        if (accountCreationYear != null) sb.append("Created: ").append(accountCreationYear).append('\n');
        return sb.toString().trim();
    }
}
