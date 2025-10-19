package click.dailyfeed.batch.config.activity.publisher.restore;

import click.dailyfeed.batch.domain.redisdlq.document.RedisDLQDocument;
import click.dailyfeed.batch.domain.redisdlq.repository.mongo.RedisDLQRepository;
import click.dailyfeed.code.domain.activity.transport.MemberActivityTransportDto;
import click.dailyfeed.code.domain.activity.type.MemberActivityType;
import click.dailyfeed.kafka.domain.activity.redis.MemberActivityEventDLQRedisService;
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
public class ActivityPublisherRestoreFromRedisBatchConfig {

    private final RedisDLQRepository redisDLQRepository;
    private final MemberActivityEventDLQRedisService memberActivityEventDLQRedisService;
    private final ObjectMapper objectMapper;

    public ActivityPublisherRestoreFromRedisBatchConfig(
            RedisDLQRepository redisDLQRepository,
            MemberActivityEventDLQRedisService memberActivityEventDLQRedisService,
            ObjectMapper objectMapper) {
        this.redisDLQRepository = redisDLQRepository;
        this.memberActivityEventDLQRedisService = memberActivityEventDLQRedisService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Job activityKafkaFailureRecoveryFromRedisJob(
            JobRepository jobRepository,
            Step activityKafkaFailureRecoveryFromRedisStep) {
        return new JobBuilder("activityKafkaFailureRecoveryFromRedisJob", jobRepository)
                .start(activityKafkaFailureRecoveryFromRedisStep)
                .build();
    }

    @Bean
    public Step activityKafkaFailureRecoveryFromRedisStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<List<MemberActivityTransportDto.MemberActivityMessage>> activityKafkaFailureRedisReader,
            ItemProcessor<List<MemberActivityTransportDto.MemberActivityMessage>, List<RedisDLQDocument>> activityKafkaFailureRedisProcessor,
            ItemWriter<List<RedisDLQDocument>> activityKafkaFailureRedisDLQWriter) {
        return new StepBuilder("activityKafkaFailureRecoveryFromRedisStep", jobRepository)
                .<List<MemberActivityTransportDto.MemberActivityMessage>, List<RedisDLQDocument>>chunk(1, transactionManager)
                .reader(activityKafkaFailureRedisReader)
                .processor(activityKafkaFailureRedisProcessor)
                .writer(activityKafkaFailureRedisDLQWriter)
                .build();
    }

    // Reader에서 읽은 메시지를 추적하기 위한 ThreadLocal
    private static final ThreadLocal<List<MemberActivityTransportDto.MemberActivityMessage>> currentBatchMessages =
            new ThreadLocal<>();

    @Bean
    @StepScope
    public ItemReader<List<MemberActivityTransportDto.MemberActivityMessage>> activityKafkaFailureRedisReader() {
        return new ItemReader<List<MemberActivityTransportDto.MemberActivityMessage>>() {
            private static final int BATCH_SIZE = 30;
            private int activityTypeIndex = 0;
            private final MemberActivityType[] activityTypes = MemberActivityType.values();

            @Override
            public List<MemberActivityTransportDto.MemberActivityMessage> read() {
                // 다음 MemberActivityType에서 배치 읽기
                while (activityTypeIndex < activityTypes.length) {
                    MemberActivityType activityType = activityTypes[activityTypeIndex++];

                    try {
                        List<MemberActivityTransportDto.MemberActivityMessage> messages =
                                memberActivityEventDLQRedisService.lPopTopN(activityType, BATCH_SIZE);
                        log.debug("Reading batch from Redis for activityType: {}", activityType);

                        if (messages != null && !messages.isEmpty()) {
                            log.info("Fetched {} messages from Redis for activityType: {}",
                                    messages.size(), activityType);

                            // 현재 읽은 메시지를 ThreadLocal에 저장 (롤백용)
                            currentBatchMessages.set(new ArrayList<>(messages));

                            return messages;
                        } else {
                            log.debug("No messages found for activityType: {}", activityType);
                        }
                    } catch (Exception e) {
                        log.error("Error reading from Redis for activityType: {}", activityType, e);
                    }
                }

                // 모든 타입을 다 읽었으면 null 반환 (읽기 종료)
                log.info("Finished reading all messages from Redis DLQ");
                return null;
            }
        };
    }

    @Bean
    @StepScope
    public ItemProcessor<List<MemberActivityTransportDto.MemberActivityMessage>, List<RedisDLQDocument>> activityKafkaFailureRedisProcessor() {
        return messages -> {
            if (messages == null || messages.isEmpty()) {
                log.debug("Empty message list, skipping processing");
                return null;
            }

            List<RedisDLQDocument> documents = new ArrayList<>();

            for (MemberActivityTransportDto.MemberActivityMessage message : messages) {
                try {
                    // JSON으로 직렬화
                    String jsonValue = objectMapper.writeValueAsString(message);

                    // RedisDLQDocument 생성
                    RedisDLQDocument document = RedisDLQDocument.newRedisDLQ(
                            message.getKey(),
                            jsonValue
                    );

                    documents.add(document);
                    log.debug("Successfully processed message: activityType={}", message.getEvent().getMemberActivityType());

                } catch (Exception e) {
                    log.error("Error processing message: activityType={}", message.getEvent().getMemberActivityType(), e);

                    // 에러 발생 시에도 DLQ에 저장 시도
                    try {
                        memberActivityEventDLQRedisService.rPush(message);
                    } catch (Exception ex) {
                        log.error("Failed to create RedisDLQDocument for error case", ex);
                    }
                }
            }

            log.info("Processed {} messages into {} documents", messages.size(), documents.size());
            return documents.isEmpty() ? null : documents;
        };
    }

    @Bean
    @StepScope
    public ItemWriter<List<RedisDLQDocument>> activityKafkaFailureRedisDLQWriter() {
        return chunk -> {
            List<RedisDLQDocument> documentsToSave = new ArrayList<>();

            // chunk.getItems()는 List<List<RedisDLQDocument>> 이므로 flatten 필요
            for (List<RedisDLQDocument> documentList : chunk.getItems()) {
                if (documentList != null && !documentList.isEmpty()) {
                    documentsToSave.addAll(documentList);
                }
            }

            if (documentsToSave.isEmpty()) {
                log.info("No documents to save in this chunk");
                // ThreadLocal 정리
                currentBatchMessages.remove();
                return;
            }

            try {
                // MongoDB에 저장 시도
                redisDLQRepository.saveAll(documentsToSave);
                log.info("Saved {} Redis DLQ documents to MongoDB (Redis keys preserved for later processing)",
                        documentsToSave.size());

                // 성공 시 ThreadLocal 정리
                currentBatchMessages.remove();

            } catch (Exception e) {
                log.error("Failed to save documents to MongoDB. Rolling back to Redis DLQ.", e);

                // 실패 시: ThreadLocal에 저장된 원본 메시지를 Redis에 다시 push
                List<MemberActivityTransportDto.MemberActivityMessage> originalMessages = currentBatchMessages.get();
                if (originalMessages != null && !originalMessages.isEmpty()) {
                    try {
                        // 원본 메시지를 Redis DLQ에 다시 push
                        memberActivityEventDLQRedisService.rPushList(originalMessages);
                        log.warn("Rolled back {} messages to Redis DLQ after MongoDB save failure",
                                originalMessages.size());
                    } catch (Exception rollbackEx) {
                        log.error("CRITICAL: Failed to rollback messages to Redis DLQ. Data may be lost!", rollbackEx);
                    } finally {
                        currentBatchMessages.remove();
                    }
                }

                // 예외를 다시 던져서 Spring Batch가 실패를 인지하도록 함
                throw e;
            }
        };
    }
}