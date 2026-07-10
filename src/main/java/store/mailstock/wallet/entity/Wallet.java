package store.mailstock.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "wallets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class Wallet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private Long userId;
    @Column(nullable = false, precision = 14, scale = 2) @Builder.Default private BigDecimal pendingBalance = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) @Builder.Default private BigDecimal availableBalance = BigDecimal.ZERO;
    @Column(nullable = false, precision = 14, scale = 2) @Builder.Default private BigDecimal totalEarnings = BigDecimal.ZERO;
    @CreatedDate @Column(nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
}
