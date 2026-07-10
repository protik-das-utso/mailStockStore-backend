package store.mailstock.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class Order {
    public enum Status { PENDING_PAYMENT, AWAITING_VERIFICATION, PAID, DELIVERED, CANCELLED, REFUNDED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long buyerId;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false, precision = 14, scale = 2) @Builder.Default private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(length = 60) private String couponCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private Status status = Status.PENDING_PAYMENT;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @CreatedDate @Column(nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
    private Instant completedAt;
}
