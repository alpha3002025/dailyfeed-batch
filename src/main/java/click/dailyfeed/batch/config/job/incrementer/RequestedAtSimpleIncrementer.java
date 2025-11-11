package click.dailyfeed.batch.config.job.incrementer;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersIncrementer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RequestedAtSimpleIncrementer implements JobParametersIncrementer {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS");

    @Override
    public JobParameters getNext(JobParameters parameters) {
        JobParametersBuilder builder = new JobParametersBuilder(parameters);
        LocalDateTime now = LocalDateTime.now();

        // requestedAt 이 없을 경우 현재시간을 주입
        if (parameters.getString("requestedAt") == null) {
            builder.addString("requestedAt", now.format(DATE_TIME_FORMATTER));
        }

        return builder.toJobParameters();
    }
}
