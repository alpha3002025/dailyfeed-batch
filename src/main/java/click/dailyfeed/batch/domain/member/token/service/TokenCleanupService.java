package click.dailyfeed.batch.domain.member.token.service;

import click.dailyfeed.batch.domain.member.token.repository.jpa.RefreshTokenRepository;
import click.dailyfeed.batch.domain.member.token.repository.jpa.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Service
public class TokenCleanupService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    /**
     * 만료된 토큰들을 정리
     * - 만료된 Refresh Token 삭제
     * - 만료된 Blacklist 항목 삭제
     */
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        log.info("🧹 Starting token cleanup at {}", now);

        var expiredRefreshTokens = refreshTokenRepository.findExpiredTokens(now);
        refreshTokenRepository.deleteAll(expiredRefreshTokens);
        log.info("Deleted {} expired refresh tokens", expiredRefreshTokens.size());

        var expiredBlacklistTokens = tokenBlacklistRepository.findExpiredTokens(now);
        tokenBlacklistRepository.deleteAll(expiredBlacklistTokens);
        log.info("Deleted {} expired blacklist tokens", expiredBlacklistTokens.size());

        log.info("✅ Token cleanup completed. Total deleted: {} tokens",
                expiredRefreshTokens.size() + expiredBlacklistTokens.size());
    }
}
