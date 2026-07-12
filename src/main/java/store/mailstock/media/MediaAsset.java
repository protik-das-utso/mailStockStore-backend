package store.mailstock.media;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An admin-uploaded image (deposit QR, site logo, hero) stored in the database so it survives
 * redeploys — the container filesystem does not. Keyed by a short stable name.
 */
@Entity
@Table(name = "media_assets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MediaAsset {

    @Id
    @Column(length = 40)
    private String name;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    // Plain byte[] maps to Postgres bytea; deliberately NOT @Lob (that would use the large-object API,
    // which fails outside a manual transaction on Postgres).
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
