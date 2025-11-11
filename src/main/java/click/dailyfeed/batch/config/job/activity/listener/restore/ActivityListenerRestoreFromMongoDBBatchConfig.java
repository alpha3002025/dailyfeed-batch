package click.dailyfeed.batch.config.job.activity.listener.restore;

import click.dailyfeed.batch.config.job.incrementer.RequestedAtSimpleIncrementer;
import click.dailyfeed.batch.domain.activity.deadletters.document.ListenerDeadLetterDocument;
import click.dailyfeed.batch.domain.activity.deadletters.repository.mongo.ListenerDeadLetterRepository;
import click.dailyfeed.batch.domain.activity.member.document.MemberActivityDocument;
import click.dailyfeed.batch.domain.activity.member.mapper.MemberActivityMapper;
import click.dailyfeed.batch.domain.activity.member.repository.mongo.MemberActivityMongoRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class ActivityListenerRestoreFromMongoDBBatchConfig {

    private final ListenerDeadLetterRepository listenerDeadLetterRepository;
    private final MemberActivityMongoRepository memberActivityMongoRepository;
    private final MemberActivityMapper memberActivityMapper;
    private final ObjectMapper objectMapper;

    // ThreadLocal로 현재 처리 중인 ListenerDeadLetterDocument들을 추적 (롤백용)
    private static final ThreadLocal<List<ListenerDeadLetterDocument>> currentBatchDocuments =
            new ThreadLocal<>();

    public ActivityListenerRestoreFromMongoDBBatchConfig(
            ListenerDeadLetterRepository listenerDeadLetterRepository,
            MemberActivityMongoRepository memberActivityMongoRepository,
            MemberActivityMapper memberActivityMapper,
            ObjectMapper objectMapper) {
        this.listenerDeadLetterRepository = listenerDeadLetterRepository;
        this.memberActivityMongoRepository = memberActivityMongoRepository;
        this.memberActivityMapper = memberActivityMapper;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Job activityListenerRestoreFromMongoDBJob(
            JobRepository jobRepository,
            Step activityListenerRestoreFromMongoDBStep) {
        return new JobBuilder("activityListenerRestoreFromMongoDBJob", jobRepository)
                .incrementer(new RequestedAtSimpleIncrementer())
                .start(activityListenerRestoreFromMongoDBStep)
                .build();
    }

    @Bean
    public Step activityListenerRestoreFromMongoDBStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<List<ListenerDeadLetterDocument>> listenerDeadLetterReader,
            ItemProcessor<List<ListenerDeadLetterDocument>, List<MemberActivityDocument>> listenerDeadLetterProcessor,
            ItemWriter<List<MemberActivityDocument>> memberActivityWriter) {
        return new StepBuilder("activityListenerRestoreFromMongoDBStep", jobRepository)
                .<List<ListenerDeadLetterDocument>, List<MemberActivityDocument>>chunk(1, transactionManager)
                .reader(listenerDeadLetterReader)
                .processor(listenerDeadLetterProcessor)
                .writer(memberActivityWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<List<ListenerDeadLetterDocument>> listenerDeadLetterReader() {
        return new ItemReader<List<ListenerDeadLetterDocument>>() {
            private static final int BATCH_SIZE = 30;
            private boolean hasMore = true;

            @Override
            public List<ListenerDeadLetterDocument> read() {
                if (!hasMore) {
                    return null;
                }

                try {
                    log.debug("Reading batch from ListenerDeadLetter (isCompleted=false)");
                    Pageable pageable = PageRequest.of(0, BATCH_SIZE);

                    List<ListenerDeadLetterDocument> documents =
                            listenerDeadLetterRepository.findByIsCompletedOrderByCreatedAtDesc(Boolean.FALSE, pageable);

                    if (documents != null && !documents.isEmpty()) {
                        log.info("Fetched {} ListenerDeadLetter documents", documents.size());

                        // ThreadLocal에 저장 (롤백용)
                        currentBatchDocuments.set(new ArrayList<>(documents));

                        // 30건 미만이면 다음 읽기에서 null 반환
                        if (documents.size() < BATCH_SIZE) {
                            hasMore = false;
                        }

                        return documents;
                    } else {
                        log.info("No more ListenerDeadLetter documents to process");
                        hasMore = false;
                        return null;
                    }
                } catch (Exception e) {
                    log.error("Error reading from ListenerDeadLetterRepository", e);
                    hasMore = false;
                    return null;
                }
            }
        };
    }

    @Bean
    @StepScope
    public ItemProcessor<List<ListenerDeadLetterDocument>, List<MemberActivityDocument>> listenerDeadLetterProcessor() {
        return documents -> {
            if (documents.isEmpty()) {
                log.debug("Empty document list, skipping processing");
                return null;
            }

            List<MemberActivityDocument> memberActivityDocuments = new ArrayList<>();

            for (ListenerDeadLetterDocument document : documents) {
                try {
                    log.debug("Processing ListenerDeadLetterDocument: messageKey={}", document.getMessageKey());

                    // JSON 페이로드를 MemberActivityMessage로 변환
                    MemberActivityTransportDto.MemberActivityMessage memberActivityMessage =
                            objectMapper.readValue(
                                    document.getPayload(),
                                    MemberActivityTransportDto.MemberActivityMessage.class
                            );

                    // MemberActivityDocument 생성 (messageKey 포함)
                    MemberActivityDocument memberActivityDocument =
                            memberActivityMapper.fromEvent(
                                    memberActivityMessage.getEvent(),
                                    document.getMessageKey()
                            );

                    memberActivityDocuments.add(memberActivityDocument);

                    log.debug("Successfully converted to MemberActivityDocument: messageKey={}",
                            document.getMessageKey());

                } catch (Exception e) {
                    log.error("Error processing ListenerDeadLetterDocument: messageKey={}, error={}",
                            document.getMessageKey(), e.getMessage(), e);
                    // 처리 실패한 항목은 건너뜀
                }
            }

            log.info("Processed {} documents into {} MemberActivityDocuments", documents.size(), memberActivityDocuments.size());
            return memberActivityDocuments.isEmpty() ? null : memberActivityDocuments;
        };
    }

    @Bean
    @StepScope
    public ItemWriter<List<MemberActivityDocument>> memberActivityWriter() {
        return chunk -> {
            List<MemberActivityDocument> allMemberActivities = new ArrayList<>();

            // chunk.getItems()는 List<List<MemberActivityDocument>> 이므로 flatten
            for (List<MemberActivityDocument> memberActivityList : chunk.getItems()) {
                if (memberActivityList != null && !memberActivityList.isEmpty()) {
                    allMemberActivities.addAll(memberActivityList);
                }
            }

            if (allMemberActivities.isEmpty()) {
                log.info("No documents to save in this chunk");
                currentBatchDocuments.remove();
                return;
            }

            try {
                // 1. 중복 제거: member_activities에 이미 존재하는 messageKey 찾기
                List<String> messageKeys = allMemberActivities.stream()
                        .map(MemberActivityDocument::getMessageKey)
                        .collect(Collectors.toList());

                List<MemberActivityDocument> existingDocs =
                        memberActivityMongoRepository.findByMessageKeyIn(messageKeys);

                Set<String> existingMessageKeys = existingDocs.stream()
                        .map(MemberActivityDocument::getMessageKey)
                        .collect(Collectors.toSet());

                log.info("Found {} existing messageKeys in member_activities", existingMessageKeys.size());

                // 2. 중복되지 않은 항목만 필터링
                List<MemberActivityDocument> memberActivitiesToSave = allMemberActivities.stream()
                        .filter(doc -> !existingMessageKeys.contains(doc.getMessageKey()))
                        .collect(Collectors.toList());

                log.info("Filtered out {} duplicates. {} documents will be inserted.",
                        existingMessageKeys.size(), memberActivitiesToSave.size());

                // 3. member_activities 컬렉션에 저장
                if (!memberActivitiesToSave.isEmpty()) {
                    memberActivityMongoRepository.saveAll(memberActivitiesToSave);
                    log.info("Saved {} member activities to member_activities collection",
                            memberActivitiesToSave.size());
                }

                // 4. listener_dead_letters 문서의 isCompleted를 true로 업데이트
                List<ListenerDeadLetterDocument> documentsToUpdate = currentBatchDocuments.get();
                if (documentsToUpdate != null && !documentsToUpdate.isEmpty()) {
                    int updatedCount = 0;
                    for (ListenerDeadLetterDocument doc : documentsToUpdate) {
                        try {
                            doc.markAsCompleted();
                            listenerDeadLetterRepository.save(doc);
                            updatedCount++;
                            log.debug("Marked as completed: messageKey={}", doc.getMessageKey());
                        } catch (Exception e) {
                            log.error("Failed to update isCompleted for messageKey={}: {}",
                                    doc.getMessageKey(), e.getMessage(), e);
                        }
                    }

                    log.info("Updated {} listener_dead_letters documents to isCompleted=true", updatedCount);
                }

                // 성공 시 ThreadLocal 정리
                currentBatchDocuments.remove();

            } catch (Exception e) {
                log.error("Failed to save member activities. Transaction will rollback.", e);

                // ThreadLocal 정리
                currentBatchDocuments.remove();

                // 예외를 다시 던져서 Spring Batch가 실패를 인지하도록 함
                throw e;
            }
        };
    }
}