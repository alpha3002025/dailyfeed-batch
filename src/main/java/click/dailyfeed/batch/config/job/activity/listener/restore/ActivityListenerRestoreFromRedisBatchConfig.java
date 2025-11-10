package click.dailyfeed.batch.config.job.activity.listener.restore;

import click.dailyfeed.batch.domain.activity.deadletters.document.ListenerDeadLetterDocument;
import click.dailyfeed.batch.domain.activity.deadletters.repository.mongo.ListenerDeadLetterRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class ActivityListenerRestoreFromRedisBatchConfig {

    private final ListenerDeadLetterRepository listenerDeadLetterRepository;
    private final MemberActivityEventDLQRedisService memberActivityEventDLQRedisService;
    private final ObjectMapper objectMapper;

    // ThreadLocal로 현재 처리 중인 MemberActivityMessage들을 추적 (롤백용)
    private static final ThreadLocal<List<MemberActivityTransportDto.MemberActivityMessage>> currentBatchMessages =
            new ThreadLocal<>();

    public ActivityListenerRestoreFromRedisBatchConfig(
            ListenerDeadLetterRepository listenerDeadLetterRepository,
            MemberActivityEventDLQRedisService memberActivityEventDLQRedisService,
            ObjectMapper objectMapper) {
        this.listenerDeadLetterRepository = listenerDeadLetterRepository;
        this.memberActivityEventDLQRedisService = memberActivityEventDLQRedisService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Job activityListenerRestoreFromRedisJob(
            JobRepository jobRepository,
            Step activityListenerRestoreFromRedisStep) {
        return new JobBuilder("activityListenerRestoreFromRedisJob", jobRepository)
                .start(activityListenerRestoreFromRedisStep)
                .build();
    }

    @Bean
    public Step activityListenerRestoreFromRedisStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<List<MemberActivityTransportDto.MemberActivityMessage>> activityListenerRedisReader,
            ItemProcessor<List<MemberActivityTransportDto.MemberActivityMessage>, List<ListenerDeadLetterDocument>> activityListenerRedisProcessor,
            ItemWriter<List<ListenerDeadLetterDocument>> listenerDeadLetterWriter) {
        return new StepBuilder("activityListenerRestoreFromRedisStep", jobRepository)
                .<List<MemberActivityTransportDto.MemberActivityMessage>, List<ListenerDeadLetterDocument>>chunk(1, transactionManager)
                .reader(activityListenerRedisReader)
                .processor(activityListenerRedisProcessor)
                .writer(listenerDeadLetterWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<List<MemberActivityTransportDto.MemberActivityMessage>> activityListenerRedisReader() {
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
                        log.debug("Reading batch from Redis DLQ for activityType: {}", activityType);
                        List<MemberActivityTransportDto.MemberActivityMessage> messages =
                                memberActivityEventDLQRedisService.lPopTopN(activityType, BATCH_SIZE);

                        if (messages != null && !messages.isEmpty()) {
                            log.info("Fetched {} messages from Redis DLQ for activityType: {}",
                                    messages.size(), activityType);

                            // ThreadLocal에 저장 (롤백용)
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
    public ItemProcessor<List<MemberActivityTransportDto.MemberActivityMessage>, List<ListenerDeadLetterDocument>> activityListenerRedisProcessor() {
        return messages -> {
            if (messages.isEmpty()) {
                log.debug("Empty message list, skipping processing");
                return null;
            }

            List<ListenerDeadLetterDocument> documents = new ArrayList<>();

            for (MemberActivityTransportDto.MemberActivityMessage message : messages) {
                try {
                    log.debug("Processing MemberActivityMessage: messageKey={}", message.getKey());

                    // JSON으로 직렬화
                    String jsonValue = objectMapper.writeValueAsString(message);

                    // ListenerDeadLetterDocument 생성
                    ListenerDeadLetterDocument document = ListenerDeadLetterDocument.newDeadLetter(
                            message.getKey(),  // messageKey
                            jsonValue,         // payload
                            message.getEvent().getCreatedAt()  // publishedAt
                    );

                    documents.add(document);

                    log.debug("Successfully converted to ListenerDeadLetterDocument: messageKey={}",
                            message.getKey());

                } catch (Exception e) {
                    log.error("Error processing MemberActivityMessage: messageKey={}, error={}",
                            message.getKey(), e.getMessage(), e);
                    // 처리 실패한 항목은 건너뜀
                }
            }

            log.info("Processed {} messages into {} ListenerDeadLetterDocuments",
                    messages.size(), documents.size());
            return documents.isEmpty() ? null : documents;
        };
    }

    @Bean
    @StepScope
    public ItemWriter<List<ListenerDeadLetterDocument>> listenerDeadLetterWriter() {
        return chunk -> {
            List<ListenerDeadLetterDocument> allDocuments = new ArrayList<>();

            // chunk.getItems()는 List<List<ListenerDeadLetterDocument>> 이므로 flatten
            for (List<ListenerDeadLetterDocument> documentList : chunk.getItems()) {
                if (documentList != null && !documentList.isEmpty()) {
                    allDocuments.addAll(documentList);
                }
            }

            if (allDocuments.isEmpty()) {
                log.info("No documents to save in this chunk");
                currentBatchMessages.remove();
                return;
            }

            try {
                // 1. 중복 제거: listener_dead_letters에 이미 존재하는 messageKey 찾기
                List<String> messageKeys = allDocuments.stream()
                        .map(ListenerDeadLetterDocument::getMessageKey)
                        .collect(Collectors.toList());

                List<ListenerDeadLetterDocument> existingDocs = messageKeys.stream()
                        .flatMap(messageKey -> listenerDeadLetterRepository.findByMessageKey(messageKey).stream())
                        .collect(Collectors.toList());

                Set<String> existingMessageKeys = existingDocs.stream()
                        .map(ListenerDeadLetterDocument::getMessageKey)
                        .collect(Collectors.toSet());

                log.info("Found {} existing messageKeys in listener_dead_letters", existingMessageKeys.size());

                // 2. 중복되지 않은 항목만 필터링
                List<ListenerDeadLetterDocument> documentsToSave = allDocuments.stream()
                        .filter(doc -> !existingMessageKeys.contains(doc.getMessageKey()))
                        .collect(Collectors.toList());

                log.info("Filtered out {} duplicates. {} documents will be inserted.",
                        existingMessageKeys.size(), documentsToSave.size());

                // 3. listener_dead_letters 컬렉션에 저장
                if (!documentsToSave.isEmpty()) {
                    listenerDeadLetterRepository.saveAll(documentsToSave);
                    log.info("Saved {} ListenerDeadLetterDocuments to listener_dead_letters collection",
                            documentsToSave.size());
                }

                // 성공 시 ThreadLocal 정리
                currentBatchMessages.remove();

            } catch (Exception e) {
                log.error("Failed to save ListenerDeadLetterDocuments to MongoDB. Rolling back to Redis DLQ.", e);

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