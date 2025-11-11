package click.dailyfeed.batch.domain.member.token.repository.jpa;

import click.dailyfeed.batch.domain.member.token.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

    @Query("SELECT tb FROM TokenBlacklist tb WHERE tb.expiresAt < :now")
    List<TokenBlacklist> findExpiredTokens(@Param("now") LocalDateTime now);
}
