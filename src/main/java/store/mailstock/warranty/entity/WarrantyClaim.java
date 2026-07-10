package store.mailstock.warranty.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity @Table(name = "warranty_claims")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class WarrantyClaim {
    public enum Status { OPEN, PENDING, RESOLVED, CLOSED }
    public enum Resolution { REPLACE, REFUND, REJECT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long orderItemId;
    @Column(nullable = false) private Long buyerId;
    @Column(nullable = false, length = 60) private String reason;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String evidenceUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private Status status = Status.OPEN;
    @Enumerated(EnumType.STRING) @Column(length = 30) private Resolution resolution;
    @Column(columnDefinition = "TEXT") private String adminNote;
    private Long resolvedBy;
    private Instant resolvedAt;
    @CreatedDate @Column(nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
}
