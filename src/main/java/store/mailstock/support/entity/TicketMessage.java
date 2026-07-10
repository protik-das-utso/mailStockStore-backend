package store.mailstock.support.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "ticket_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long ticketId;
    @Column(nullable = false) private Long senderId;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Column(columnDefinition = "TEXT") private String attachmentUrl;
    @Column(nullable = false, updatable = false) @Builder.Default private Instant createdAt = Instant.now();
}
