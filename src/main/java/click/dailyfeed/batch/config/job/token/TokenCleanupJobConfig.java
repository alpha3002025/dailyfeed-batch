package click.dailyfeed.batch.config.job.token;

import click.dailyfeed.batch.domain.token.service.TokenCleanupService;
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
public class TokenCleanupJobConfig {

    private final TokenCleanupService tokenCleanupService;

    @Bean
    public Job tokenCleanupJob(
            JobRepository jobRepository,
            Step tokenCleanupStep) {
        return new JobBuilder("tokenCleanupJob", jobRepository)
                .start(tokenCleanupStep)
                .build();
    }

    @Bean
    public Step tokenCleanupStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            Tasklet tokenCleanupTasklet) {
        return new StepBuilder("tokenCleanupStep", jobRepository)
                .tasklet(tokenCleanupTasklet, transactionManager)
                .build();
    }

    @Bean
    public Tasklet tokenCleanupTasklet() {
        return (contribution, chunkContext) -> {
            log.info("Executing Token Cleanup Tasklet");
            tokenCleanupService.cleanupExpiredTokens();
            return RepeatStatus.FINISHED;
        };
    }
}
