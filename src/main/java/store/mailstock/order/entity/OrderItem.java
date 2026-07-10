package store.mailstock.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    // Owning side of the FK — Hibernate writes order_id in the initial insert (no deferred update).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    // Read-only view of the FK so derived queries like findByOrderId keep working.
    @Column(name = "order_id", insertable = false, updatable = false) private Long orderId;
    @Column(nullable = false) private Long inventoryId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal price;
    @Column(nullable = false) @Builder.Default private Integer warrantyDays = 0;
    private Instant warrantyExpiresAt;
    // Credentials snapshot delivered to the buyer (email, password, 2FA, recovery…).
    @Column(columnDefinition = "TEXT") private String deliveryPayload;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
