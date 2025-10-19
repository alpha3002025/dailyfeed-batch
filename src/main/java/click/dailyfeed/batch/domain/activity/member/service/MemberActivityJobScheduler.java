package click.dailyfeed.batch.domain.activity.member.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@EnableScheduling
@Profile("local-was")  // local-was 프로필에서만 활성화
public class MemberActivityJobScheduler {
    private final JobLauncher jobLauncher;
    private final Job activityKafkaFailureRecoveryJob;

    public MemberActivityJobScheduler(
            JobLauncher jobLauncher,
            @Qualifier("activityKafkaFailureRecoveryFromFileJob") Job activityKafkaFailureRecoveryJob) {
        this.jobLauncher = jobLauncher;
        this.activityKafkaFailureRecoveryJob = activityKafkaFailureRecoveryJob;
    }

    @Scheduled(fixedRate = 1000) // 1초마다 실행 (개발/테스트용)
    public void runBatchJob() {
        try {
            log.info("Starting scheduled batch job: activityKafkaFailureRecoveryJob");

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(activityKafkaFailureRecoveryJob, jobParameters);

            log.info("Successfully completed scheduled batch job");
        } catch (Exception e) {
            log.error("Failed to execute batch job", e);
        }
    }
}
