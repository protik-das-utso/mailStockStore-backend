package store.mailstock.support.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity @Table(name = "support_tickets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class SupportTicket {
    public enum Status { OPEN, PENDING, ANSWERED, RESOLVED, CLOSED }
    public enum Priority { LOW, NORMAL, HIGH, URGENT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 200) private String subject;
    @Column(length = 50) private String category;
    /** Optional order this ticket is about, for support context. */
    @Column(name = "order_id") private Long orderId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private Status status = Status.OPEN;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) @Builder.Default private Priority priority = Priority.NORMAL;

    // --- activity & unread tracking ---
    @Column(name = "last_message_at") private Instant lastMessageAt;
    /** True when the last message was from staff (drives the "Support replied" hint). */
    @Column(name = "last_sender_staff", nullable = false) @Builder.Default private boolean lastSenderStaff = false;
    @Column(name = "user_read_at") private Instant userReadAt;
    @Column(name = "admin_read_at") private Instant adminReadAt;

    @CreatedDate @Column(nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
    private Instant closedAt;
}
