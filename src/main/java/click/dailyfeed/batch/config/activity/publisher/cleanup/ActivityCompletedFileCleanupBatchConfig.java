package click.dailyfeed.batch.config.activity.publisher.cleanup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class ActivityCompletedFileCleanupBatchConfig {

    private static final String COMPLETED_PREFIX = "completed--";
    private static final int DEFAULT_RETENTION_DAYS = 7;

    // 삭제 결과를 담는 내부 클래스
    private static class DeletionResult {
        private final File file;
        private final boolean deleted;
        private final String reason;

        public DeletionResult(File file, boolean deleted, String reason) {
            this.file = file;
            this.deleted = deleted;
            this.reason = reason;
        }

        public File getFile() {
            return file;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public String getReason() {
            return reason;
        }
    }

    @Bean
    public Job activityCompletedFileCleanupJob(JobRepository jobRepository, Step activityCompletedFileCleanupStep) {
        return new JobBuilder("activityCompletedFileCleanupJob", jobRepository)
                .start(activityCompletedFileCleanupStep)
                .build();
    }

    @Bean
    public Step activityCompletedFileCleanupStep(JobRepository jobRepository,
                                                   PlatformTransactionManager transactionManager,
                                                   MultiResourceItemReader<File> completedFileReader,
                                                   ItemProcessor<File, DeletionResult> completedFileProcessor,
                                                   ItemWriter<DeletionResult> completedFileWriter) {
        return new StepBuilder("activityCompletedFileCleanupStep", jobRepository)
                .<File, DeletionResult>chunk(50, transactionManager)
                .reader(completedFileReader)
                .processor(completedFileProcessor)
                .writer(completedFileWriter)
                .build();
    }

    @Bean
    @StepScope
    public MultiResourceItemReader<File> completedFileReader(
            @Value("#{jobParameters['failurePath'] ?: '${batch.failure-path:kafka-failure-storage}'}")
            String failurePath) {
        MultiResourceItemReader<File> reader = new MultiResourceItemReader<>();
        reader.setResources(getCompletedFiles(failurePath));
        reader.setDelegate(new org.springframework.batch.item.file.ResourceAwareItemReaderItemStream<File>() {
            private Resource currentResource;

            @Override
            public File read() {
                if (currentResource != null) {
                    File file = ((FileSystemResource) currentResource).getFile();
                    currentResource = null;
                    return file;
                }
                return null;
            }

            @Override
            public void setResource(Resource resource) {
                this.currentResource = resource;
            }

            @Override
            public void open(org.springframework.batch.item.ExecutionContext executionContext) {}

            @Override
            public void update(org.springframework.batch.item.ExecutionContext executionContext) {}

            @Override
            public void close() {}
        });
        return reader;
    }

    private Resource[] getCompletedFiles(String failurePath) {
        try {
            Path path = Paths.get(failurePath);
            if (!Files.exists(path)) {
                log.warn("Failure path does not exist: {}", failurePath);
                return new Resource[0];
            }

            // 'completed--' 접두사를 가진 .json 파일만 읽기
            List<Resource> resources = new ArrayList<>();
            try (var stream = Files.walk(path, 1)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> p.getFileName().toString().startsWith(COMPLETED_PREFIX))
                        .forEach(p -> resources.add(new FileSystemResource(p.toFile())));
            }

            log.info("Found {} completed files to cleanup", resources.size());
            return resources.toArray(new Resource[0]);
        } catch (Exception e) {
            log.error("Error reading completed files from directory", e);
            throw new RuntimeException("Failed to read completed files from directory", e);
        }
    }

    @Bean
    @StepScope
    public ItemProcessor<File, DeletionResult> completedFileProcessor(
            @Value("#{jobParameters['retentionDays'] ?: " + DEFAULT_RETENTION_DAYS + "}")
            int retentionDays) {
        return file -> {
            try {
                log.debug("Processing completed file: {}", file.getName());

                // 파일의 마지막 수정 시간 확인
                long lastModifiedMillis = file.lastModified();
                Instant lastModified = Instant.ofEpochMilli(lastModifiedMillis);
                Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

                // 보존 기간이 지난 파일만 삭제 대상으로 반환
                if (lastModified.isBefore(cutoffTime)) {
                    log.debug("File {} is older than {} days, marking for deletion",
                            file.getName(), retentionDays);
                    return new DeletionResult(file, false, "pending");
                } else {
                    log.debug("File {} is within retention period, skipping",
                            file.getName());
                    return null;
                }
            } catch (Exception e) {
                log.error("Error processing completed file: {}", file.getName(), e);
                return new DeletionResult(file, false, "error: " + e.getMessage());
            }
        };
    }

    @Bean
    @StepScope
    public ItemWriter<DeletionResult> completedFileWriter() {
        return chunk -> {
            int deletedCount = 0;
            int failedCount = 0;
            int skippedCount = 0;

            for (DeletionResult result : chunk.getItems()) {
                if (result == null) {
                    skippedCount++;
                    continue;
                }

                File file = result.getFile();
                try {
                    if (file.exists()) {
                        if (file.delete()) {
                            deletedCount++;
                            log.info("Successfully deleted completed file: {}", file.getName());
                        } else {
                            failedCount++;
                            log.warn("Failed to delete completed file: {}", file.getName());
                        }
                    } else {
                        skippedCount++;
                        log.debug("File does not exist (already deleted?): {}", file.getName());
                    }
                } catch (Exception e) {
                    failedCount++;
                    log.error("Error deleting completed file: {}", file.getName(), e);
                }
            }

            log.info("Cleanup summary: {} deleted, {} failed, {} skipped",
                    deletedCount, failedCount, skippedCount);
        };
    }
}
