package click.dailyfeed.batch.config.activity.publisher.restore;

import click.dailyfeed.batch.domain.activity.member.document.MemberActivityDocument;
import click.dailyfeed.batch.domain.activity.member.mapper.MemberActivityMapper;
import click.dailyfeed.batch.domain.activity.member.repository.mongo.MemberActivityMongoRepository;
import click.dailyfeed.batch.domain.redisdlq.document.RedisDLQDocument;
import click.dailyfeed.batch.domain.redisdlq.repository.mongo.RedisDLQRepository;
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
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class ActivityPublisherRestoreFromMongoDBBatchConfig {

    private final RedisDLQRepository redisDLQRepository;
    private final MemberActivityMongoRepository memberActivityMongoRepository;
    private final MemberActivityMapper memberActivityMapper;
    private final ObjectMapper objectMapper;

    private static final int MAX_UNPROCESSED_DOCUMENTS = 100;

    public ActivityPublisherRestoreFromMongoDBBatchConfig(
            RedisDLQRepository redisDLQRepository,
            MemberActivityMongoRepository memberActivityMongoRepository,
            MemberActivityMapper memberActivityMapper,
            ObjectMapper objectMapper) {
        this.redisDLQRepository = redisDLQRepository;
        this.memberActivityMongoRepository = memberActivityMongoRepository;
        this.memberActivityMapper = memberActivityMapper;
        this.objectMapper = objectMapper;
    }

    // RedisDLQDocument와 MemberActivityDocument를 함께 전달하기 위한 내부 클래스
    private static class ProcessedItem {
        private final RedisDLQDocument redisDLQDocument;
        private final MemberActivityDocument memberActivityDocument;

        public ProcessedItem(RedisDLQDocument redisDLQDocument, MemberActivityDocument memberActivityDocument) {
            this.redisDLQDocument = redisDLQDocument;
            this.memberActivityDocument = memberActivityDocument;
        }

        public RedisDLQDocument getRedisDLQDocument() {
            return redisDLQDocument;
        }

        public MemberActivityDocument getMemberActivityDocument() {
            return memberActivityDocument;
        }
    }

    @Bean
    public Job activityKafkaFailureRecoveryFromMongoDBJob(
            JobRepository jobRepository,
            Step activityKafkaFailureRecoveryFromMongoDBStep) {
        return new JobBuilder("activityKafkaFailureRecoveryFromMongoDBJob", jobRepository)
                .start(activityKafkaFailureRecoveryFromMongoDBStep)
                .build();
    }

    @Bean
    public Step activityKafkaFailureRecoveryFromMongoDBStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<RedisDLQDocument> redisDLQDocumentReader,
            ItemProcessor<RedisDLQDocument, ProcessedItem> redisDLQDocumentProcessor,
            ItemWriter<ProcessedItem> memberActivityWriter) {
        return new StepBuilder("activityKafkaFailureRecoveryFromMongoDBStep", jobRepository)
                .<RedisDLQDocument, ProcessedItem>chunk(10, transactionManager)
                .reader(redisDLQDocumentReader)
                .processor(redisDLQDocumentProcessor)
                .writer(memberActivityWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<RedisDLQDocument> redisDLQDocumentReader() {
        return new ItemReader<RedisDLQDocument>() {
            private List<RedisDLQDocument> documents;
            private int currentIndex = 0;
            private boolean initialized = false;

            @Override
            public RedisDLQDocument read() {
                if (!initialized) {
                    initialize();
                    initialized = true;
                }

                if (documents == null || currentIndex >= documents.size()) {
                    return null;
                }

                return documents.get(currentIndex++);
            }

            private void initialize() {
                log.info("Fetching unprocessed documents from redis_dead_letters (isCompleted=false, isEditing=false)");

                // isCompleted=false AND isEditing=false 문서 조회
                documents = redisDLQRepository.findByIsCompletedFalseAndIsEditingFalse();

                log.info("Found {} unprocessed documents", documents.size());

                // 100개를 초과하면 Job 실패
                if (documents.size() > MAX_UNPROCESSED_DOCUMENTS) {
                    String errorMsg = String.format(
                            "Too many unprocessed documents detected (%d > %d). Job cannot proceed. " +
                            "This indicates a large backlog that requires separate handling.",
                            documents.size(), MAX_UNPROCESSED_DOCUMENTS
                    );
                    log.error(errorMsg);
                    throw new IllegalStateException(errorMsg);
                }
            }
        };
    }

    @Bean
    @StepScope
    public ItemProcessor<RedisDLQDocument, ProcessedItem> redisDLQDocumentProcessor() {
        return redisDLQDocument -> {
            try {
                log.debug("Processing RedisDLQDocument: redisKey={}", redisDLQDocument.getMessageKey());

                // JSON 페이로드를 MemberActivityMessage로 변환
                MemberActivityTransportDto.MemberActivityMessage memberActivityMessage =
                        objectMapper.readValue(
                                redisDLQDocument.getPayload(),
                                MemberActivityTransportDto.MemberActivityMessage.class
                        );

                // MemberActivityDocument 생성
                MemberActivityDocument memberActivityDocument =
                        memberActivityMapper.fromEvent(memberActivityMessage.getEvent());

                log.debug("Successfully converted to MemberActivityDocument: redisKey={}", redisDLQDocument.getMessageKey());

                // RedisDLQDocument와 MemberActivityDocument 함께 반환
                return new ProcessedItem(redisDLQDocument, memberActivityDocument);

            } catch (Exception e) {
                log.error("Error processing RedisDLQDocument: redisKey={}, error={}", redisDLQDocument.getMessageKey(), e.getMessage(), e);
                // 처리 실패 시 null 반환 (건너뜀)
                return null;
            }
        };
    }

    @Bean
    @StepScope
    public ItemWriter<ProcessedItem> memberActivityWriter() {
        return chunk -> {
            List<MemberActivityDocument> memberActivitiesToSave = new ArrayList<>();
            List<RedisDLQDocument> redisDLQsToUpdate = new ArrayList<>();

            // ProcessedItem에서 분리
            for (ProcessedItem item : chunk.getItems()) {
                if (item != null) {
                    memberActivitiesToSave.add(item.getMemberActivityDocument());
                    redisDLQsToUpdate.add(item.getRedisDLQDocument());
                }
            }

            if (memberActivitiesToSave.isEmpty()) {
                log.info("No documents to process in this chunk");
                return;
            }

            try {
                // member_activities 컬렉션에 저장
                memberActivityMongoRepository.saveAll(memberActivitiesToSave);
                log.info("Saved {} member activities to member_activities collection",
                        memberActivitiesToSave.size());

                // redis_dead_letters 문서의 isCompleted를 true로 업데이트
                int updatedCount = 0;
                for (RedisDLQDocument redisDLQDoc : redisDLQsToUpdate) {
                    try {
                        // isCompleted를 true로 업데이트
                        redisDLQDoc.markAsCompleted();
                        redisDLQRepository.save(redisDLQDoc);
                        updatedCount++;
                        log.debug("Marked as completed: redisKey={}", redisDLQDoc.getMessageKey());
                    } catch (Exception e) {
                        log.error("Failed to update isCompleted for redisKey={}: {}",
                                redisDLQDoc.getMessageKey(), e.getMessage(), e);
                    }
                }

                log.info("Updated {} redis_dead_letters documents to isCompleted=true", updatedCount);

            } catch (Exception e) {
                log.error("Failed to save member activities. Rolling back transaction.", e);
                throw e; // 트랜잭션 롤백
            }
        };
    }
}
