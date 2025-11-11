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
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class JwtKeyInitService {
    private final JwtKeyRepository jwtKeyRepository;

    @Value("${jwt.key.rotation.hours:24}")
    private int keyRotationHours;

    @Value("${jwt.key.grace.period.hours:48}")
    private int gracePeriodHours;

    /**
     * JWT 키 초기화 작업 수행
     * 1. 중복된 Primary Key 정리
     * 2. Primary Key가 없으면 새로 생성
     */
    public void initializeJwtKey() {
        log.info("🔑 Starting JWT Key Initialization...");

        fixDuplicatePrimaryKeys();

        Optional<JwtKey> primaryKey = jwtKeyRepository.findPrimaryKey();
        if (primaryKey.isEmpty()) {
            log.info("⚠️ No primary key found, generating new one");
            generateNewPrimaryKey();
            log.info("✅ New primary key generated successfully");
        } else {
            JwtKey key = primaryKey.get();
            log.info("✅ Primary key already exists: {} (created at: {}, expires at: {})",
                    key.getKeyId(), key.getCreatedAt(), key.getExpiresAt());
        }

        log.info("✅ JWT Key Initialization completed successfully");
    }

    /**
     * 중복된 Primary Key 정리
     * 가장 최신 키만 Primary로 유지하고 나머지는 일반 키로 변경
     */
    private void fixDuplicatePrimaryKeys() {
        List<JwtKey> primaryKeys = jwtKeyRepository.findAllPrimaryKeys();

        if (primaryKeys.size() > 1) {
            log.warn("⚠️ Found {} primary keys, fixing duplicate primary keys...", primaryKeys.size());

            primaryKeys.stream()
                    .sorted((k1, k2) -> k2.getCreatedAt().compareTo(k1.getCreatedAt()))
                    .skip(1)
                    .forEach(key -> {
                        log.warn("Demoting duplicate primary key: {} (created at: {})",
                                key.getKeyId(), key.getCreatedAt());
                        key.disablePrimaryKey();
                        jwtKeyRepository.save(key);
                    });

            log.info("✅ Fixed duplicate primary keys, kept the latest key as primary");
        } else if (primaryKeys.size() == 1) {
            log.debug("✅ Primary key status is healthy (1 primary key found)");
        } else {
            log.debug("No primary key found yet, will generate new one");
        }
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
}
