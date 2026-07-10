package store.mailstock.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 50) private String type;
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String body;
    @Column(length = 300) private String link;
    private Instant readAt;
    @Builder.Default
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
}
