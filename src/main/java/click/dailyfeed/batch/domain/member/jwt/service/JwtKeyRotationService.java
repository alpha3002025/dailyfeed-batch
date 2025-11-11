package click.dailyfeed.batch.domain.member.jwt.service;

import click.dailyfeed.batch.domain.member.jwt.entity.JwtKey;
import click.dailyfeed.batch.domain.member.jwt.repository.jpa.JwtKeyRepository;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class JwtKeyRotationService {
    private final JwtKeyRepository jwtKeyRepository;

    @Value("${jwt.key.rotation.hours:24}")
    private int keyRotationHours;

    @Value("${jwt.key.grace.period.hours:48}")
    private int gracePeriodHours;

    /**
     * 키 로테이션이 필요한 경우 새 키를 생성하고, 만료된 키들을 정리
     */
    public void rotateKeysIfNeeded() {
        log.debug("🔄 Checking if key rotation is needed...");

        Optional<JwtKey> currentPrimary = jwtKeyRepository.findPrimaryKey();

        if (currentPrimary.isEmpty()) {
            log.warn("⚠️ No primary key found during scheduled rotation, generating new one");
            generateNewPrimaryKey();
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime keyCreatedAt = currentPrimary.get().getCreatedAt();

        // 현재 Primary Key가 KEY_ROTATION_HOURS 이상 지난 경우 새 키 생성
        if (keyCreatedAt.plusHours(keyRotationHours).isBefore(now)) {
            log.info("🔄 Key rotation triggered: current key is {} hours old (threshold: {} hours)",
                    java.time.Duration.between(keyCreatedAt, now).toHours(), keyRotationHours);
            generateNewPrimaryKey();
        } else {
            log.debug("✅ Current key is still valid (created {} hours ago, rotation at {} hours)",
                    java.time.Duration.between(keyCreatedAt, now).toHours(), keyRotationHours);
        }

        // 만료된 키들 정리
        cleanupExpiredKeys();
    }

    /**
     * 새로운 Primary Key 생성
     */
    private void generateNewPrimaryKey() {
        log.info("🔑 Generating new primary key...");

        List<JwtKey> existingPrimaryKeys = jwtKeyRepository.findAllPrimaryKeys();
        if (!existingPrimaryKeys.isEmpty()) {
            for (JwtKey existing : existingPrimaryKeys) {
                existing.disablePrimaryKey();
                jwtKeyRepository.save(existing);
                log.info("Demoted existing primary key: {} to regular key", existing.getKeyId());
            }
        }

        SecretKey secretKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());

        JwtKey newKey = JwtKey.newKey(encodedKey, keyRotationHours, gracePeriodHours);
        jwtKeyRepository.save(newKey);

        log.info("✅ New primary key generated with ID: {} (will expire at: {})",
                newKey.getKeyId(), newKey.getExpiresAt());
    }

    /**
     * 만료된 키들 정리
     */
    private void cleanupExpiredKeys() {
        LocalDateTime now = LocalDateTime.now();
        List<JwtKey> expiredKeys = jwtKeyRepository.findExpiredKeys(now);

        if (!expiredKeys.isEmpty()) {
            for (JwtKey expiredKey : expiredKeys) {
                expiredKey.deactivate();
                log.info("Deactivated expired key: {} (expired at: {})",
                        expiredKey.getKeyId(), expiredKey.getExpiresAt());
            }

            jwtKeyRepository.saveAll(expiredKeys);
            log.info("✅ Cleaned up {} expired keys", expiredKeys.size());
        } else {
            log.debug("✅ No expired keys to clean up");
        }
    }
}
