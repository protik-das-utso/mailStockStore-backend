package store.mailstock.recovery.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A public, unguessable link scoped to ONE Google account. Anyone holding the token can view that
 * account's latest recovery code (no login) — so the token is the secret. Scoping to {@code accountEmail}
 * guarantees a link can never surface a different account's code even when many accounts share a mailbox.
 */
@Entity @Table(name = "recovery_links")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecoveryLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(nullable = false, length = 64, unique = true) private String token;
    @Column(name = "mailbox_id", nullable = false) private Long mailboxId;

    /** The account whose recovery email is being set — the reader only matches messages naming this. */
    @Column(name = "account_email", nullable = false, length = 255) private String accountEmail;

    @Column(name = "inventory_id") private Long inventoryId;
    @Column(name = "order_item_id") private Long orderItemId;

    @Column(nullable = false) @Builder.Default private Boolean revoked = false;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();

    @Transient
    public boolean isUsable() {
        return !Boolean.TRUE.equals(revoked) && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
