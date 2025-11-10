package click.dailyfeed.batch.domain.member.token.entity;

import click.dailyfeed.batch.domain.member.jwt.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Table(name = "jwt_refresh_tokens",
    indexes = {
        @Index(name = "idx_member_id", columnList = "member_id"),
        @Index(name = "idx_access_token_id", columnList = "access_token_id"),
        @Index(name = "idx_expires_at", columnList = "expires_at"),
        @Index(name = "idx_token_value", columnList = "token_value")
    }
)
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "token_id", unique = true, nullable = false)
    private String tokenId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "token_value", nullable = false, length = 512, unique = true)
    private String tokenValue;

    @Column(name = "access_token_id", nullable = false)
    private String accessTokenId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_revoked", nullable = false)
    private Boolean isRevoked;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;
}
