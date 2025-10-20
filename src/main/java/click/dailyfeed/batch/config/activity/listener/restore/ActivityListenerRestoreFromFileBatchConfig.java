package click.dailyfeed.batch.config.activity.listener.restore;

import click.dailyfeed.batch.domain.activity.deadletters.document.ListenerDeadLetterDocument;
import click.dailyfeed.batch.domain.activity.deadletters.mongo.ListenerDeadLetterRepository;
import click.dailyfeed.batch.domain.activity.member.mapper.MemberActivityMapper;
import click.dailyfeed.code.domain.activity.transport.MemberActivityTransportDto;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class ActivityListenerRestoreFromFileBatchConfig {

    private final ListenerDeadLetterRepository listenerDeadLetterRepository;
    private final MemberActivityMapper memberActivityMapper;
    private final ObjectMapper objectMapper;

    public ActivityListenerRestoreFromFileBatchConfig(
            ListenerDeadLetterRepository listenerDeadLetterRepository,
            MemberActivityMapper memberActivityMapper,
            ObjectMapper objectMapper) {
        this.listenerDeadLetterRepository = listenerDeadLetterRepository;
        this.memberActivityMapper = memberActivityMapper;
        this.objectMapper = objectMapper;
    }

    private static final String EDITING_PREFIX = "editing--";
    private static final String COMPLETED_PREFIX = "completed--";
    private static final int MAX_EDITING_FILES = 100;

    // File과 Document를 함께 전달하기 위한 내부 클래스
    private static class ProcessedItem {
        private final File sourceFile;
        private final File editingFile;
        private final ListenerDeadLetterDocument document;

        public ProcessedItem(File sourceFile, File editingFile, ListenerDeadLetterDocument document) {
            this.sourceFile = sourceFile;
            this.editingFile = editingFile;
            this.document = document;
        }

        public File getSourceFile() {
            return sourceFile;
        }

        public File getEditingFile() {
            return editingFile;
        }

        public ListenerDeadLetterDocument getDocument() {
            return document;
        }
    }

    @Bean
    public Job activityListenerRestoreFromFileJob(
            JobRepository jobRepository,
            Step activityListenerRestoreFromFileStep) {
        return new JobBuilder("activityListenerRestoreFromFileJob", jobRepository)
                .start(activityListenerRestoreFromFileStep)
                .build();
    }

    @Bean
    public Step activityListenerRestoreFromFileStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MultiResourceItemReader<File> activityListenerFileReader,
            ItemProcessor<File, ProcessedItem> activityListenerFileProcessor,
            ItemWriter<ProcessedItem> activityListenerDeadLetterWriter) {
        return new StepBuilder("activityListenerRestoreFromFileStep", jobRepository)
                .<File, ProcessedItem>chunk(10, transactionManager)
                .reader(activityListenerFileReader)
                .processor(activityListenerFileProcessor)
                .writer(activityListenerDeadLetterWriter)
                .build();
    }

    @Bean
    @StepScope
    public MultiResourceItemReader<File> activityListenerFileReader(
            @Value("#{jobParameters['failurePath'] ?: '${batch.listener-file-path:listener-failure-storage}'}") String failurePath) {
        MultiResourceItemReader<File> reader = new MultiResourceItemReader<>();
        reader.setResources(getJsonFiles(failurePath));
        reader.setDelegate(new org.springframework.batch.item.file.ResourceAwareItemReaderItemStream<File>() {
            private Resource currentResource;

            @Override
            public File read() {
                if (currentResource != null) {
                    File file = ((FileSystemResource) currentResource).getFile();
                    currentResource = null; // 한 번만 읽도록
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

    private Resource[] getJsonFiles(String failurePath) {
        try {
            Path path = Paths.get(failurePath);
            if (!Files.exists(path)) {
                log.warn("Failure path does not exist: {}", failurePath);
                return new Resource[0];
            }

            // 'editing--' 접두사를 가진 파일 개수 확인
            long editingFileCount;
            try (var stream = Files.walk(path, 1)) {
                editingFileCount = stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().startsWith(EDITING_PREFIX))
                        .count();
            }

            if (editingFileCount >= MAX_EDITING_FILES) {
                String errorMsg = String.format(
                        "Too many editing files detected (%d >= %d). Job cannot proceed. " +
                        "This indicates possible stuck processes or unfinished previous executions.",
                        editingFileCount, MAX_EDITING_FILES
                );
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            log.info("Editing files in directory: {}", editingFileCount);

            // 'editing--' 및 'completed--' 접두사가 없는 .json 파일만 읽기
            List<Resource> resources = new ArrayList<>();
            try (var stream = Files.walk(path, 1)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> {
                            String fileName = p.getFileName().toString();
                            return !fileName.startsWith(EDITING_PREFIX)
                                    && !fileName.startsWith(COMPLETED_PREFIX);
                        })
                        .forEach(p -> resources.add(new FileSystemResource(p.toFile())));
            }

            log.info("Found {} JSON files to process (excluding editing and completed files)", resources.size());
            return resources.toArray(new Resource[0]);
        } catch (Exception e) {
            log.error("Error reading failure directory", e);
            throw new RuntimeException("Failed to read failure directory", e);
        }
    }

    @Bean
    @StepScope
    public ItemProcessor<File, ProcessedItem> activityListenerFileProcessor() {
        return originalFile -> {
            File editingFile = null;
            try {
                log.info("Processing file: {}", originalFile.getName());

                // Step 1: 파일 이름을 'editing--' 접두사로 변경 (잠금)
                String editingFileName = EDITING_PREFIX + originalFile.getName();
                editingFile = new File(originalFile.getParent(), editingFileName);

                boolean renamed = originalFile.renameTo(editingFile);
                if (!renamed) {
                    log.error("Failed to rename file to editing state: {} -> {}",
                            originalFile.getName(), editingFileName);
                    return null;
                }

                log.debug("File locked for editing: {}", editingFileName);

                // Step 2: JSON 파일 읽기 및 변환
                MemberActivityTransportDto.MemberActivityMessage memberActivityMessage =
                        memberActivityMapper.fromLogFilePayload(editingFile);

                // Step 3: ListenerDeadLetterDocument 생성
                String jsonPayload = objectMapper.writeValueAsString(memberActivityMessage);
                ListenerDeadLetterDocument document = ListenerDeadLetterDocument.newDeadLetter(
                        memberActivityMessage.getKey(),  // messageKey
                        jsonPayload,                     // payload
                        memberActivityMessage.getEvent().getCreatedAt()  // publishedAt
                );

                // File과 Document를 함께 반환 (파일 처리는 Writer에서 수행)
                return new ProcessedItem(originalFile, editingFile, document);

            } catch (Exception e) {
                log.error("Error processing file: {}", originalFile.getName(), e);

                // 실패 시 원본 파일명으로 복원
                if (editingFile != null && editingFile.exists()) {
                    boolean restored = editingFile.renameTo(originalFile);
                    if (restored) {
                        log.info("File restored to original name after error: {}", originalFile.getName());
                    } else {
                        log.error("Failed to restore original filename: {}", originalFile.getName());
                    }

                    // 복원 실패 시 .error 파일로 변경
                    if (!restored) {
                        File errorFile = new File(editingFile.getParent(), originalFile.getName() + ".error");
                        boolean errorRenamed = editingFile.renameTo(errorFile);
                        if (!errorRenamed) {
                            log.warn("Failed to rename file to .error: {}", editingFile.getName());
                        }
                    }
                }

                return null;
            }
        };
    }

    @Bean
    @StepScope
    public ItemWriter<ProcessedItem> activityListenerDeadLetterWriter() {
        return chunk -> {
            List<ListenerDeadLetterDocument> documentsToSave = new ArrayList<>();
            List<ProcessedItem> itemsToProcess = new ArrayList<>();

            // ProcessedItem에서 Document 추출
            for (ProcessedItem item : chunk.getItems()) {
                if (item != null && item.getDocument() != null) {
                    documentsToSave.add(item.getDocument());
                    itemsToProcess.add(item);
                }
            }

            if (documentsToSave.isEmpty()) {
                log.info("No documents to save in this chunk");
                return;
            }

            try {
                // MongoDB에 저장 (트랜잭션 내에서 실행)
                listenerDeadLetterRepository.saveAll(documentsToSave);
                log.info("Saved {} ListenerDeadLetterDocuments to MongoDB", documentsToSave.size());

                // 저장 성공 후 'editing--' 파일을 'completed--' 접두사로 변경
                int completedCount = 0;
                int failedCount = 0;
                for (ProcessedItem item : itemsToProcess) {
                    File editingFile = item.getEditingFile();
                    File originalFile = item.getSourceFile();
                    try {
                        if (editingFile != null && editingFile.exists()) {
                            // editing--원본파일명.json -> completed--원본파일명.json
                            String completedFileName = COMPLETED_PREFIX + originalFile.getName();
                            File completedFile = new File(editingFile.getParent(), completedFileName);

                            if (editingFile.renameTo(completedFile)) {
                                completedCount++;
                                log.debug("Successfully renamed to completed: {} -> {}",
                                        editingFile.getName(), completedFile.getName());
                            } else {
                                failedCount++;
                                log.warn("Failed to rename to completed: {} -> {}",
                                        editingFile.getName(), completedFileName);
                            }
                        } else {
                            log.warn("Editing file does not exist: {}",
                                    editingFile != null ? editingFile.getName() : "null");
                        }
                    } catch (Exception e) {
                        failedCount++;
                        log.error("Error renaming editing file to completed: {}",
                                editingFile != null ? editingFile.getName() : "null", e);
                    }
                }

                log.info("File completion summary: {} completed, {} failed", completedCount, failedCount);

            } catch (Exception e) {
                log.error("MongoDB save failed. Restoring original filenames...", e);

                // MongoDB 저장 실패 시 'editing--' 접두사 제거하여 원상복구
                int restoredCount = 0;
                int restoreFailedCount = 0;
                for (ProcessedItem item : itemsToProcess) {
                    File editingFile = item.getEditingFile();
                    File originalFile = item.getSourceFile();

                    try {
                        if (editingFile != null && editingFile.exists()) {
                            boolean restored = editingFile.renameTo(originalFile);
                            if (restored) {
                                restoredCount++;
                                log.info("Restored original filename: {}", originalFile.getName());
                            } else {
                                restoreFailedCount++;
                                log.error("Failed to restore original filename: {} -> {}",
                                        editingFile.getName(), originalFile.getName());
                            }
                        }
                    } catch (Exception restoreEx) {
                        restoreFailedCount++;
                        log.error("Error restoring filename: {} -> {}",
                                editingFile != null ? editingFile.getName() : "null",
                                originalFile.getName(), restoreEx);
                    }
                }

                log.info("File restoration summary: {} restored, {} failed", restoredCount, restoreFailedCount);

                // MongoDB 예외를 다시 던져서 Spring Batch 트랜잭션 롤백
                throw e;
            }
        };
    }
}