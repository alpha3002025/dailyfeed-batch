package click.dailyfeed.batch.config.job.member.jwt;

import click.dailyfeed.batch.domain.member.jwt.service.JwtKeyRotationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * JWT Key Rotation Job 테스트
 */
@SpringBatchTest
@SpringBootTest(classes = {
        click.dailyfeed.batch.config.TestBatchConfig.class,
        click.dailyfeed.batch.config.job.member.jwt.JwtKeyRotationJobConfig.class
})
@ActiveProfiles("test")
@DisplayName("JWT Key Rotation Job 테스트")
class JwtKeyRotationJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @MockBean
    private JwtKeyRotationService jwtKeyRotationService;

    @Autowired
    private Job jwtKeyRotationJob;

    @Test
    @DisplayName("JWT Key Rotation Job이 성공적으로 실행되어야 한다")
    void shouldExecuteJwtKeyRotationJobSuccessfully() throws Exception {
        // given
        jobLauncherTestUtils.setJob(jwtKeyRotationJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        doNothing().when(jwtKeyRotationService).rotateKeysIfNeeded();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        // Service 메서드가 한 번 호출되었는지 확인
        verify(jwtKeyRotationService, times(1)).rotateKeysIfNeeded();
    }

    @Test
    @DisplayName("JWT Key Rotation Job 이름이 올바르게 설정되어야 한다")
    void shouldHaveCorrectJobName() {
        // when & then
        assertThat(jwtKeyRotationJob.getName()).isEqualTo("jwtKeyRotationJob");
    }

    @Test
    @DisplayName("Service에서 예외 발생 시 Job이 실패해야 한다")
    void shouldFailWhenServiceThrowsException() throws Exception {
        // given
        jobLauncherTestUtils.setJob(jwtKeyRotationJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        doThrow(new RuntimeException("Key rotation failed"))
                .when(jwtKeyRotationService).rotateKeysIfNeeded();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

        // Service 메서드가 호출되었는지 확인
        verify(jwtKeyRotationService, times(1)).rotateKeysIfNeeded();
    }

    @Test
    @DisplayName("Step이 성공적으로 실행되어야 한다")
    void shouldExecuteStepSuccessfully() throws Exception {
        // given
        jobLauncherTestUtils.setJob(jwtKeyRotationJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        doNothing().when(jwtKeyRotationService).rotateKeysIfNeeded();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStepExecutions()).hasSize(1);
        assertThat(jobExecution.getStepExecutions().iterator().next().getStepName())
                .isEqualTo("jwtKeyRotationStep");
        assertThat(jobExecution.getStepExecutions().iterator().next().getStatus())
                .isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("다른 JobParameters로 여러 번 실행 가능해야 한다")
    void shouldAllowMultipleExecutionsWithDifferentParameters() throws Exception {
        // given
        jobLauncherTestUtils.setJob(jwtKeyRotationJob);

        JobParameters jobParameters1 = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        // 약간 다른 시간으로 두 번째 파라미터 생성
        Thread.sleep(10);
        JobParameters jobParameters2 = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        doNothing().when(jwtKeyRotationService).rotateKeysIfNeeded();

        // when
        JobExecution jobExecution1 = jobLauncherTestUtils.launchJob(jobParameters1);
        JobExecution jobExecution2 = jobLauncherTestUtils.launchJob(jobParameters2);

        // then
        assertThat(jobExecution1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution2.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 두 번 모두 실행되었는지 확인 (다른 JobExecution ID)
        assertThat(jobExecution1.getId()).isNotEqualTo(jobExecution2.getId());

        // Service 메서드가 두 번 호출되었는지 확인
        verify(jwtKeyRotationService, times(2)).rotateKeysIfNeeded();
    }
}
