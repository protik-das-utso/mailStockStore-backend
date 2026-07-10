package store.mailstock.wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "withdraw_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class WithdrawRequest {
    public enum Status { PENDING, APPROVED, REJECTED, PAID }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 30) @Builder.Default private String method = "BINANCE";
    @Column(nullable = false, length = 200) private String destination;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private Status status = Status.PENDING;
    @Column(columnDefinition = "TEXT") private String adminNote;
    private Long processedBy;
    private Instant processedAt;
    @Column(length = 200) private String payoutTxid;
    @CreatedDate @Column(nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
}
