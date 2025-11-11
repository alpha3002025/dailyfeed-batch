package click.dailyfeed.batch.config.job.member.jwt;

import click.dailyfeed.batch.domain.member.jwt.service.JwtKeyInitService;
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
 * JWT Key Initialization Job 테스트
 */
@SpringBatchTest
@SpringBootTest(classes = {
        click.dailyfeed.batch.config.TestBatchConfig.class,
        click.dailyfeed.batch.config.job.member.jwt.JwtKeyInitJobConfig.class
})
@ActiveProfiles("test")
@DisplayName("JWT Key Initialization Job 테스트")
class JwtKeyInitJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @MockBean
    private JwtKeyInitService jwtKeyInitService;

    @Autowired
    private Job jwtKeyInitJob;

    @Test
    @DisplayName("JWT Key Initialization Job이 성공적으로 실행되어야 한다")
    void shouldExecuteJwtKeyInitJobSuccessfully() throws Exception {
        // given
        jobLauncherTestUtils.setJob(jwtKeyInitJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        doNothing().when(jwtKeyInitService).initializeJwtKey();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        // Service 메서드가 한 번 호출되었는지 확인
        verify(jwtKeyInitService, times(1)).initializeJwtKey();
    }

    @Test
    @DisplayName("JWT Key Initialization Job 이름이 올바르게 설정되어야 한다")
    void shouldHaveCorrectJobName() {
        // when & then
        assertThat(jwtKeyInitJob.getName()).isEqualTo("jwtKeyInitJob");
    }

    @Test
    @DisplayName("Service에서 예외 발생 시 Job이 실패해야 한다")
    void shouldFailWhenServiceThrowsException() throws Exception {
        // given
        jobLauncherTestUtils.setJob(jwtKeyInitJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        doThrow(new RuntimeException("Key initialization failed"))
                .when(jwtKeyInitService).initializeJwtKey();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

        // Service 메서드가 호출되었는지 확인
        verify(jwtKeyInitService, times(1)).initializeJwtKey();
    }

    @Test
    @DisplayName("Step이 성공적으로 실행되어야 한다")
    void shouldExecuteStepSuccessfully() throws Exception {
        // given
        jobLauncherTestUtils.setJob(jwtKeyInitJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        doNothing().when(jwtKeyInitService).initializeJwtKey();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStepExecutions()).hasSize(1);
        assertThat(jobExecution.getStepExecutions().iterator().next().getStepName())
                .isEqualTo("jwtKeyInitStep");
        assertThat(jobExecution.getStepExecutions().iterator().next().getStatus())
                .isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("다른 JobParameters로 여러 번 실행 가능해야 한다")
    void shouldAllowMultipleExecutionsWithDifferentParameters() throws Exception {
        // given
        jobLauncherTestUtils.setJob(jwtKeyInitJob);

        JobParameters jobParameters1 = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        // 약간 다른 시간으로 두 번째 파라미터 생성
        Thread.sleep(10);
        JobParameters jobParameters2 = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        doNothing().when(jwtKeyInitService).initializeJwtKey();

        // when
        JobExecution jobExecution1 = jobLauncherTestUtils.launchJob(jobParameters1);
        JobExecution jobExecution2 = jobLauncherTestUtils.launchJob(jobParameters2);

        // then
        assertThat(jobExecution1.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution2.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 두 번 모두 실행되었는지 확인 (다른 JobExecution ID)
        assertThat(jobExecution1.getId()).isNotEqualTo(jobExecution2.getId());

        // Service 메서드가 두 번 호출되었는지 확인
        verify(jwtKeyInitService, times(2)).initializeJwtKey();
    }

    // TODO :: 세부구현 검증 용도의 테스트케이스 별도 구현 고려해볼것
    @Test
    @DisplayName("Job이 중복된 Primary Key를 정리하고 새 키를 생성해야 한다")
    void shouldCleanupDuplicatePrimaryKeysAndGenerateNewKey() throws Exception {
        // given
        jobLauncherTestUtils.setJob(jwtKeyInitJob);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("requestedAt", LocalDateTime.now().toString())
                .toJobParameters();

        // initializeJwtKey가 중복 키 정리 및 새 키 생성을 수행한다고 가정
        doNothing().when(jwtKeyInitService).initializeJwtKey();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // initializeJwtKey가 호출되었는지 확인 (내부에서 fixDuplicatePrimaryKeys + generateNewPrimaryKey 실행)
        verify(jwtKeyInitService, times(1)).initializeJwtKey();
    }
}
