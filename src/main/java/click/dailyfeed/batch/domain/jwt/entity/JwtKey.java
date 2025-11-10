package click.dailyfeed.batch.domain.jwt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "jwt_keys")
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class JwtKey extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "key_id", unique = true, nullable = false)
    private String keyId;

    @Column(name = "secret_key", nullable = false, length = 512)
    private String secretKey;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_primary", nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean isPrimary;

    @Builder(builderMethodName = "newKeyBuilder")
    public JwtKey(String keyId, String secretKey, Boolean isActive, LocalDateTime expiresAt, Boolean isPrimary) {
        this.keyId = keyId;
        this.secretKey = secretKey;
        this.isActive = isActive;
        this.expiresAt = expiresAt;
        this.isPrimary = isPrimary;
    }

    public static JwtKey newKey(String encodedKey, Integer keyRotationHours, Integer gracePeriodHours) {
        return JwtKey.newKeyBuilder()
                .keyId(UUID.randomUUID().toString())
                .secretKey(encodedKey)
                .isActive(true)
                .isPrimary(true)
                .expiresAt(LocalDateTime.now().plusHours(keyRotationHours + gracePeriodHours))
                .build();
    }

    public void disablePrimaryKey() {
        this.isPrimary = false;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
