package store.mailstock.wallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "wallet_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long walletId;
    @Column(nullable = false, length = 30) private String type; // CREDIT_SALE, DEBIT_WITHDRAW, RELEASE, HOLD, ADJUSTMENT
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "balance_after", nullable = false, precision = 14, scale = 2) private BigDecimal balanceAfter;
    @Column(length = 120) private String reference;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
