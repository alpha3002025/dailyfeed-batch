package click.dailyfeed.batch.config.job.member.token;

import click.dailyfeed.batch.domain.member.token.entity.RefreshToken;
import click.dailyfeed.batch.domain.member.token.entity.TokenBlacklist;
import click.dailyfeed.batch.domain.member.token.repository.jpa.RefreshTokenRepository;
import click.dailyfeed.batch.domain.member.token.repository.jpa.TokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Token Cleanup Job 테스트
 */
@SpringBatchTest
@SpringBootTest(classes = {
        click.dailyfeed.batch.config.TestBatchConfig.class,
        click.dailyfeed.batch.config.job.member.token.TokenCleanupJobConfig.class,
        click.dailyfeed.batch.domain.member.token.service.TokenCleanupService.class
})
@ActiveProfiles("test")
@DisplayName("Token Cleanup Job 테스트")
class TokenCleanupJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Autowired
    private Job tokenCleanupJob;

    @Test
    @DisplayName("Token Cleanup Job이 성공적으로 실행되어야 한다")
    void shouldExecuteTokenCleanupJobSuccessfully() throws Exception {
        // given
        jobLauncherTestUtils.setJob(tokenCleanupJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        when(refreshTokenRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(tokenBlacklistRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        // Repository 메서드들이 호출되었는지 확인
        verify(refreshTokenRepository, times(1)).findExpiredTokens(any(LocalDateTime.class));
        verify(tokenBlacklistRepository, times(1)).findExpiredTokens(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Token Cleanup Job 이름이 올바르게 설정되어야 한다")
    void shouldHaveCorrectJobName() {
        // when & then
        assertThat(tokenCleanupJob.getName()).isEqualTo("tokenCleanupJob");
    }

    @Test
    @DisplayName("Repository에서 예외 발생 시 Job이 실패해야 한다")
    void shouldFailWhenRepositoryThrowsException() throws Exception {
        // given
        jobLauncherTestUtils.setJob(tokenCleanupJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        when(refreshTokenRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

        // Repository 메서드가 호출되었는지 확인
        verify(refreshTokenRepository, times(1)).findExpiredTokens(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Step이 성공적으로 실행되어야 한다")
    void shouldExecuteStepSuccessfully() throws Exception {
        // given
        jobLauncherTestUtils.setJob(tokenCleanupJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        when(refreshTokenRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(tokenBlacklistRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStepExecutions()).hasSize(1);
        assertThat(jobExecution.getStepExecutions().iterator().next().getStepName())
                .isEqualTo("tokenCleanupStep");
        assertThat(jobExecution.getStepExecutions().iterator().next().getStatus())
                .isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("다른 JobParameters로 여러 번 실행 가능해야 한다")
    void shouldAllowMultipleExecutionsWithDifferentParameters() throws Exception {
        // given
        jobLauncherTestUtils.setJob(tokenCleanupJob);

        JobParameters jobParameters1 = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        // 약간 다른 시간으로 두 번째 파라미터 생성
        Thread.sleep(10);
        JobParameters jobParameters2 = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        when(refreshTokenRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(tokenBlacklistRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // when
        JobExecution jobExecution1 = jobLauncherTestUtils.launchJob(jobParameters1);
        JobExecution jobExecution2 = jobLauncherTestUtils.launchJob(jobParameters2);

        // then
        assertThat(jobExecution1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution2.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 두 번 모두 실행되었는지 확인 (다른 JobExecution ID)
        assertThat(jobExecution1.getId()).isNotEqualTo(jobExecution2.getId());

        // Repository 메서드가 두 번씩 호출되었는지 확인
        verify(refreshTokenRepository, times(2)).findExpiredTokens(any(LocalDateTime.class));
        verify(tokenBlacklistRepository, times(2)).findExpiredTokens(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Job이 만료된 RefreshToken과 TokenBlacklist를 정리해야 한다")
    void shouldCleanupExpiredRefreshTokensAndBlacklist() throws Exception {
        // given
        jobLauncherTestUtils.setJob(tokenCleanupJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        // Mock 데이터 준비
        List<RefreshToken> expiredRefreshTokens = Collections.emptyList();
        List<TokenBlacklist> expiredBlacklistTokens = Collections.emptyList();

        when(refreshTokenRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenReturn(expiredRefreshTokens);
        when(tokenBlacklistRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenReturn(expiredBlacklistTokens);

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 두 Repository의 findExpiredTokens와 deleteAll이 각각 호출되었는지 확인
        verify(refreshTokenRepository, times(1)).findExpiredTokens(any(LocalDateTime.class));
        verify(refreshTokenRepository, times(1)).deleteAll(expiredRefreshTokens);
        verify(tokenBlacklistRepository, times(1)).findExpiredTokens(any(LocalDateTime.class));
        verify(tokenBlacklistRepository, times(1)).deleteAll(expiredBlacklistTokens);
    }

    // TODO :: 추후 결과값 기반 검증 코드를 만들지 고려해볼것 (롤백된 내용 검증 - 삭제되지 않았다는 검증 (근데 좋은 방식은 아니긴 함))
    @Test
    @DisplayName("Job 실행 중 트랜잭션이 롤백되면 정리가 되지 않아야 한다")
    void shouldRollbackWhenTransactionFails() throws Exception {
        // given
        jobLauncherTestUtils.setJob(tokenCleanupJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        // Repository에서 예외를 발생시켜 트랜잭션 롤백 유도
        when(refreshTokenRepository.findExpiredTokens(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

        // Job이 실패했으므로 트랜잭션이 롤백되고 deleteAll은 호출되지 않음
        verify(refreshTokenRepository, times(1)).findExpiredTokens(any(LocalDateTime.class));
        verify(refreshTokenRepository, never()).deleteAll(any());
        verify(tokenBlacklistRepository, never()).findExpiredTokens(any(LocalDateTime.class));
        verify(tokenBlacklistRepository, never()).deleteAll(any());
    }
}
