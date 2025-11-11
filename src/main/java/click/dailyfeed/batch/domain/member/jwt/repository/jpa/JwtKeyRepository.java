package click.dailyfeed.batch.domain.member.jwt.repository.jpa;

import click.dailyfeed.batch.domain.member.jwt.entity.JwtKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JwtKeyRepository extends JpaRepository<JwtKey, Long> {

    @Query("SELECT k FROM JwtKey k WHERE k.isPrimary = true AND k.isActive = true ORDER BY k.createdAt DESC LIMIT 1")
    Optional<JwtKey> findPrimaryKey();

    @Query("SELECT k FROM JwtKey k WHERE k.isPrimary = true AND k.isActive = true")
    List<JwtKey> findAllPrimaryKeys();

    @Query("SELECT j FROM JwtKey j WHERE j.expiresAt < :now")
    List<JwtKey> findExpiredKeys(LocalDateTime now);
}
