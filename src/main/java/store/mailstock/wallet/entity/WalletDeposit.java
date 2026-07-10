package store.mailstock.wallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "wallet_deposits")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletDeposit {
    public enum Status { PENDING, APPROVED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(length = 200) private String txid;
    @Column(nullable = false, length = 30) @Builder.Default private String method = "BINANCE_MANUAL";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) @Builder.Default private Status status = Status.PENDING;
    @Column(columnDefinition = "TEXT") private String adminNote;
    private Long processedBy;
    private Instant processedAt;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
