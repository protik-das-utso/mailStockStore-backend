package store.mailstock.recovery.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A platform-owned mailbox we can read over POP3 to pull Google recovery-verification codes.
 * The POP3 password is stored AES-GCM encrypted ({@code passwordEnc}) and is never serialized to any API.
 */
@Entity @Table(name = "recovery_mailboxes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecoveryMailbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(nullable = false, length = 120) private String label;
    @Column(nullable = false, length = 255) private String email;
    @Column(nullable = false, length = 255) private String host;
    @Column(nullable = false) @Builder.Default private Integer port = 995;
    @Column(nullable = false) @Builder.Default private Boolean ssl = true;
    @Column(nullable = false, length = 255) private String username;

    /** AES-GCM ciphertext of the POP3 password. Decrypted only in-memory at fetch time. */
    @Column(name = "password_enc", nullable = false, columnDefinition = "TEXT") private String passwordEnc;

    @Column(nullable = false) @Builder.Default private Boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) @Builder.Default private Instant updatedAt = Instant.now();
}
