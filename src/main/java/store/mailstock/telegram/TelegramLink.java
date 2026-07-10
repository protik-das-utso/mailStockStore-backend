package store.mailstock.telegram;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Binding of a Telegram chat to a MailStock user (one chat ↔ one user). */
@Entity
@Table(name = "telegram_links")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TelegramLink {
    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
