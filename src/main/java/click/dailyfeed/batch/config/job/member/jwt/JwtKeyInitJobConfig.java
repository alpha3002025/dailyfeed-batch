package click.dailyfeed.batch.config.job.member.jwt;

import click.dailyfeed.batch.domain.member.jwt.service.JwtKeyInitService;
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
public class JwtKeyInitJobConfig {

    private final JwtKeyInitService jwtKeyInitService;

    @Bean
    public Job jwtKeyInitJob(
            JobRepository jobRepository,
            Step jwtKeyInitStep) {
        return new JobBuilder("jwtKeyInitJob", jobRepository)
                .start(jwtKeyInitStep)
                .build();
    }

    @Bean
    public Step jwtKeyInitStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            Tasklet jwtKeyInitTasklet) {
        return new StepBuilder("jwtKeyInitStep", jobRepository)
                .tasklet(jwtKeyInitTasklet, transactionManager)
                .build();
    }

    @Bean
    public Tasklet jwtKeyInitTasklet() {
        return (contribution, chunkContext) -> {
            log.info("Executing JWT Key Initialization Tasklet");
            jwtKeyInitService.initializeJwtKey();
            return RepeatStatus.FINISHED;
        };
    }
}
