package store.mailstock.recovery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

import store.mailstock.recovery.entity.RecoveryLink;
import store.mailstock.recovery.entity.RecoveryMailbox;

/** Request/response shapes for the recovery module. Passwords go IN but never come back OUT. */
public final class RecoveryDtos {
    private RecoveryDtos() {}

    /** Create/update a mailbox. On update, a blank {@code password} means "keep the existing one". */
    public record MailboxRequest(
            @NotBlank @Size(max = 120) String label,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 255) String host,
            @Min(1) @Max(65535) Integer port,
            Boolean ssl,
            @NotBlank @Size(max = 255) String username,
            String password,
            Boolean active) {}

    /** Seller-facing mailbox view — only what a seller needs to add & verify a recovery email. */
    public record SellerMailboxView(String email, String label) {
        public static SellerMailboxView of(RecoveryMailbox m) {
            return new SellerMailboxView(m.getEmail(), m.getLabel());
        }
    }

    /** Admin-facing mailbox view — deliberately omits the password entirely. */
    public record MailboxView(Long id, String label, String email, String host, Integer port,
                              boolean ssl, String username, boolean active, Instant createdAt) {
        public static MailboxView of(RecoveryMailbox m) {
            return new MailboxView(m.getId(), m.getLabel(), m.getEmail(), m.getHost(), m.getPort(),
                    Boolean.TRUE.equals(m.getSsl()), m.getUsername(), Boolean.TRUE.equals(m.getActive()),
                    m.getCreatedAt());
        }
    }

    /** Create a public link for one account against a chosen mailbox. */
    public record LinkRequest(
            @NotNull Long mailboxId,
            @NotBlank @Email @Size(max = 255) String accountEmail,
            Long inventoryId,
            Long orderItemId,
            Integer expiresInDays) {}

    /** Admin-facing link view. Includes the full public URL so admin can copy/share it. */
    public record LinkView(Long id, String token, String url, Long mailboxId, String accountEmail,
                           Long inventoryId, Long orderItemId, boolean revoked, Instant expiresAt,
                           Instant createdAt) {
        public static LinkView of(RecoveryLink l, String url) {
            return new LinkView(l.getId(), l.getToken(), url, l.getMailboxId(), l.getAccountEmail(),
                    l.getInventoryId(), l.getOrderItemId(), Boolean.TRUE.equals(l.getRevoked()),
                    l.getExpiresAt(), l.getCreatedAt());
        }
    }
}
