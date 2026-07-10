package store.mailstock.coupon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One row per successful coupon redemption by a user — enforces {@code perUserLimit}. */
@Entity @Table(name = "coupon_redemptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CouponRedemption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long couponId;
    @Column(nullable = false) private Long userId;
    private Long orderId;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
