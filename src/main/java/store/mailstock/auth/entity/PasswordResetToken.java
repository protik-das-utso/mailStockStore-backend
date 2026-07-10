package store.mailstock.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens", indexes = @Index(name = "idx_prt_token", columnList = "token", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 128) private String token;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Instant expiresAt;
    private Instant usedAt;
    @Builder.Default
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
}
