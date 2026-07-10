package store.mailstock.setting.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Setting {
    @Id @Column(length = 80) private String key;
    @Column(columnDefinition = "TEXT") private String value;
    @Column(nullable = false) @Builder.Default private Instant updatedAt = Instant.now();
}
