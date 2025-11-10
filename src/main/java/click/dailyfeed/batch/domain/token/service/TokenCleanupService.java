package click.dailyfeed.batch.domain.token.service;

import click.dailyfeed.batch.domain.token.repository.jpa.RefreshTokenRepository;
import click.dailyfeed.batch.domain.token.repository.jpa.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Transactional
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

        int deletedRefreshTokens = refreshTokenRepository.deleteExpiredTokens(now);
        log.info("Deleted {} expired refresh tokens", deletedRefreshTokens);

        int deletedBlacklistTokens = tokenBlacklistRepository.deleteExpiredTokens(now);
        log.info("Deleted {} expired blacklist tokens", deletedBlacklistTokens);

        log.info("✅ Token cleanup completed. Total deleted: {} tokens",
                deletedRefreshTokens + deletedBlacklistTokens);
    }
}
