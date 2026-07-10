package store.mailstock.announcement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "announcements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Announcement {
    public enum Audience { ALL, SELLERS, BUYERS }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private Audience audience = Audience.ALL;
    @Column(nullable = false) @Builder.Default private boolean active = true;
    private Instant startsAt;
    private Instant endsAt;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
