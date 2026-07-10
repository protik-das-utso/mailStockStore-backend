package store.mailstock.coupon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "coupons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Coupon {
    public enum DiscountType { PERCENT, FIXED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 60) private String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DiscountType discountType;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal discountValue;
    private Integer maxUses;
    /** Max redemptions allowed per user (NULL = unlimited per user). */
    private Integer perUserLimit;
    @Column(nullable = false) @Builder.Default private Integer usedCount = 0;
    @Column(precision = 14, scale = 2) private BigDecimal minAmount;
    private Instant startsAt;
    private Instant expiresAt;
    @Column(nullable = false) @Builder.Default private boolean active = true;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
