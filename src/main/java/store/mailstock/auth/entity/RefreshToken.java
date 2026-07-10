package store.mailstock.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_rt_token", columnList = "token", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 512) private String token;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Instant expiresAt;
    private Instant revokedAt;
    @Builder.Default
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
}
