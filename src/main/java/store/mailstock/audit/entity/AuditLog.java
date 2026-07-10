package store.mailstock.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long actorId;
    @Column(nullable = false, length = 80) private String action;
    @Column(length = 80) private String entity;
    @Column(length = 80) private String entityId;
    @Column(columnDefinition = "TEXT") private String metadata;
    @Column(length = 60) private String ip;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
