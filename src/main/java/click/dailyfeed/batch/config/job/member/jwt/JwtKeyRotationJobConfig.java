package click.dailyfeed.batch.config.job.member.jwt;

import click.dailyfeed.batch.domain.member.jwt.service.JwtKeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class JwtKeyRotationJobConfig {

    private final JwtKeyRotationService jwtKeyRotationService;

    @Bean
    public Job jwtKeyRotationJob(
            JobRepository jobRepository,
            Step jwtKeyRotationStep) {
        return new JobBuilder("jwtKeyRotationJob", jobRepository)
                .start(jwtKeyRotationStep)
                .build();
    }

    @Bean
    public Step jwtKeyRotationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            Tasklet jwtKeyRotationTasklet) {
        return new StepBuilder("jwtKeyRotationStep", jobRepository)
                .tasklet(jwtKeyRotationTasklet, transactionManager)
                .build();
    }

    @Bean
    public Tasklet jwtKeyRotationTasklet() {
        return (contribution, chunkContext) -> {
            log.info("Executing JWT Key Rotation Tasklet");
            jwtKeyRotationService.rotateKeysIfNeeded();
            return RepeatStatus.FINISHED;
        };
    }
}
