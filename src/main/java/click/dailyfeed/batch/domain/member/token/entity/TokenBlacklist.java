package click.dailyfeed.batch.domain.member.token.entity;

import click.dailyfeed.batch.domain.member.jwt.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Table(name = "jwt_blacklist",
    indexes = {
        @Index(name = "idx_token_jti", columnList = "jti"),
        @Index(name = "idx_expires_at", columnList = "expires_at"),
        @Index(name = "idx_member_id", columnList = "member_id")
    }
)
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class TokenBlacklist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "jti", unique = true, nullable = false)
    private String jti;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "reason", length = 100)
    private String reason;
}
