package store.mailstock.telegram;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A website-minted one-time code the user pastes into the bot to bind their chat. Persisted (not
 * in-memory) so codes survive restarts and work when the web node and the bot poller differ.
 */
@Entity @Table(name = "telegram_link_codes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TelegramLinkCode {
    @Id @Column(length = 16) private String code;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
