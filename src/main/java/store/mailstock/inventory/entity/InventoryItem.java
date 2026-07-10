package store.mailstock.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "inventory_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class InventoryItem {
    public enum Status { AVAILABLE, RESERVED, SOLD, ARCHIVED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long submissionId;
    private Long sellerId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 50) private String category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) @Builder.Default private store.mailstock.submission.entity.SellerSubmission.Provider provider = store.mailstock.submission.entity.SellerSubmission.Provider.GMAIL;
    @Enumerated(EnumType.STRING) @Column(length = 10) private store.mailstock.submission.entity.SellerSubmission.AccountType accountType;
    @Column(length = 80) private String country;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal purchasePrice;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal sellingPrice;
    @Column(nullable = false) @Builder.Default private Integer warrantyDays = 0;
    @Enumerated(EnumType.STRING) @Column(name = "stock_status", nullable = false, length = 20) @Builder.Default
    private Status stockStatus = Status.AVAILABLE;
    @Column(columnDefinition = "TEXT") private String deliveryPayload;
    @Column(columnDefinition = "TEXT") private String internalNotes;
    @Column(nullable = false) @Builder.Default private Instant purchaseDate = Instant.now();
    @CreatedDate @Column(nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;

    @Transient
    public BigDecimal getProfit() { return sellingPrice.subtract(purchasePrice); }
}
