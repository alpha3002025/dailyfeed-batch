package click.dailyfeed.batch.job.sample;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 샘플 배치 Job 설정
 * 기본적인 배치 작업의 구조를 보여주는 예제
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SampleJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 샘플 Job 정의
     */
    @Bean
    public Job sampleJob() {
        return new JobBuilder("sampleJob", jobRepository)
                .start(sampleStep1())
                .next(sampleStep2())
                .build();
    }

    /**
     * Step 1: 초기화 단계
     */
    @Bean
    public Step sampleStep1() {
        return new StepBuilder("sampleStep1", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info(">>>>> Sample Step 1 실행");
                    log.info(">>>>> 배치 작업 초기화 중...");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Step 2: 메인 처리 단계
     */
    @Bean
    public Step sampleStep2() {
        return new StepBuilder("sampleStep2", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info(">>>>> Sample Step 2 실행");
                    log.info(">>>>> 배치 작업 처리 중...");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
