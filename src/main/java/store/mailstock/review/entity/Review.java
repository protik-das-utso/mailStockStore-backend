package store.mailstock.review.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "reviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long inventoryId;
    @Column(nullable = false) private Long buyerId;
    @Column(nullable = false) private Integer rating;
    @Column(columnDefinition = "TEXT") private String body;
    @Column(nullable = false) @Builder.Default private boolean approved = false;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
