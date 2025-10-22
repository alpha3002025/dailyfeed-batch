package click.dailyfeed.batch.config.activity.publish.restore;

import click.dailyfeed.batch.domain.activity.member.document.MemberActivityDocument;
import click.dailyfeed.batch.domain.activity.member.repository.mongo.MemberActivityMongoRepository;
import click.dailyfeed.deadletter.domain.deadletter.document.KafkaPublisherDeadLetterDocument;
import click.dailyfeed.deadletter.domain.deadletter.repository.mongo.KafkaPublisherDeadLetterRepository;
import click.dailyfeed.code.domain.activity.dto.MemberActivityDto;
import click.dailyfeed.code.domain.activity.type.MemberActivityType;
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

@Slf4j
@Configuration
public class PublishDeadletterRestoreJobConfig {

    private final KafkaPublisherDeadLetterRepository kafkaPublisherDeadLetterRepository;
    private final MemberActivityMongoRepository memberActivityMongoRepository;
    private final ObjectMapper objectMapper;

    // ThreadLocal로 현재 처리 중인 KafkaPublisherDeadLetterDocument 들을 추적 (롤백용)
    private static final ThreadLocal<List<KafkaPublisherDeadLetterDocument>> currentBatchDocuments =
            new ThreadLocal<>();

    public PublishDeadletterRestoreJobConfig(
            KafkaPublisherDeadLetterRepository kafkaPublisherDeadLetterRepository,
            MemberActivityMongoRepository memberActivityMongoRepository,
            ObjectMapper objectMapper) {
        this.kafkaPublisherDeadLetterRepository = kafkaPublisherDeadLetterRepository;
        this.memberActivityMongoRepository = memberActivityMongoRepository;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Job publishDeadletterRestoreJob(
            JobRepository jobRepository,
            Step publishDeadletterRestoreStep) {
        return new JobBuilder("publishDeadletterRestoreJob", jobRepository)
                .start(publishDeadletterRestoreStep)
                .build();
    }

    @Bean
    public Step publishDeadletterRestoreStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<List<KafkaPublisherDeadLetterDocument>> kafkaPublisherDeadLetterReader,
            ItemProcessor<List<KafkaPublisherDeadLetterDocument>, List<MemberActivityDocument>> kafkaPublisherDeadLetterProcessor,
            ItemWriter<List<MemberActivityDocument>> publishMemberActivityWriter) {
        return new StepBuilder("publishDeadletterRestoreStep", jobRepository)
                .<List<KafkaPublisherDeadLetterDocument>, List<MemberActivityDocument>>chunk(1, transactionManager)
                .reader(kafkaPublisherDeadLetterReader)
                .processor(kafkaPublisherDeadLetterProcessor)
                .writer(publishMemberActivityWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<List<KafkaPublisherDeadLetterDocument>> kafkaPublisherDeadLetterReader() {
        return new ItemReader<List<KafkaPublisherDeadLetterDocument>>() {
            private static final int BATCH_SIZE = 50;
            private boolean hasMore = true;

            @Override
            public List<KafkaPublisherDeadLetterDocument> read() {
                if (!hasMore) {
                    return null;
                }

                try {
                    log.debug("Reading batch from KafkaPublisherDeadLetter (isCompleted=false)");
                    Pageable pageable = PageRequest.of(0, BATCH_SIZE);

                    List<KafkaPublisherDeadLetterDocument> documents =
                            kafkaPublisherDeadLetterRepository.findByIsCompletedOrderByCreatedAtDesc(Boolean.FALSE, pageable);

                    if (documents != null && !documents.isEmpty()) {
                        log.info("Fetched {} KafkaPublisherDeadLetter documents", documents.size());

                        // ThreadLocal에 저장 (롤백용)
                        currentBatchDocuments.set(new ArrayList<>(documents));

                        // 50건 미만이면 다음 읽기에서 null 반환
                        if (documents.size() < BATCH_SIZE) {
                            hasMore = false;
                        }

                        return documents;
                    } else {
                        log.info("No more KafkaPublisherDeadLetter documents to process");
                        hasMore = false;
                        return null;
                    }
                } catch (Exception e) {
                    log.error("Error reading from KafkaPublisherDeadLetterRepository", e);
                    hasMore = false;
                    return null;
                }
            }
        };
    }

    @Bean
    @StepScope
    public ItemProcessor<List<KafkaPublisherDeadLetterDocument>, List<MemberActivityDocument>> kafkaPublisherDeadLetterProcessor() {
        return documents -> {
            if (documents.isEmpty()) {
                log.debug("Empty document list, skipping processing");
                return null;
            }

            List<MemberActivityDocument> memberActivityDocuments = new ArrayList<>();

            for (KafkaPublisherDeadLetterDocument document : documents) {
                try {
                    log.debug("Processing KafkaPublisherDeadLetterDocument: category={}, id={}",
                            document.getCategory(), document.getId());

                    MemberActivityDocument memberActivityDocument = null;
                    MemberActivityType.Category category = document.getCategory();

                    switch (category) {
                        case POST:
                            MemberActivityDto.PostActivityRequest postRequest =
                                    objectMapper.readValue(document.getPayload(), MemberActivityDto.PostActivityRequest.class);
                            memberActivityDocument = MemberActivityDocument.ofNewPostActivity(
                                    postRequest.getMemberId(),
                                    postRequest.getPostId(),
                                    postRequest.getActivityType()
                            );
                            break;

                        case COMMENT:
                            MemberActivityDto.CommentActivityRequest commentRequest =
                                    objectMapper.readValue(document.getPayload(), MemberActivityDto.CommentActivityRequest.class);
                            memberActivityDocument = MemberActivityDocument.ofNewCommentActivity(
                                    commentRequest.getMemberId(),
                                    commentRequest.getPostId(),
                                    commentRequest.getCommentId(),
                                    commentRequest.getActivityType()
                            );
                            break;

                        case POST_LIKE:
                            MemberActivityDto.PostLikeActivityRequest postLikeRequest =
                                    objectMapper.readValue(document.getPayload(), MemberActivityDto.PostLikeActivityRequest.class);
                            memberActivityDocument = MemberActivityDocument.ofNewPostLikeActivity(
                                    postLikeRequest.getMemberId(),
                                    postLikeRequest.getPostId(),
                                    postLikeRequest.getActivityType()
                            );
                            break;

                        case COMMENT_LIKE:
                            MemberActivityDto.CommentLikeActivityRequest commentLikeRequest =
                                    objectMapper.readValue(document.getPayload(), MemberActivityDto.CommentLikeActivityRequest.class);
                            memberActivityDocument = MemberActivityDocument.ofNewCommentLikeActivity(
                                    commentLikeRequest.getMemberId(),
                                    commentLikeRequest.getCommentId(),
                                    commentLikeRequest.getActivityType()
                            );
                            break;

                        default:
                            log.warn("Unknown category: {}", category);
                            continue;
                    }

                    if (memberActivityDocument != null) {
                        memberActivityDocuments.add(memberActivityDocument);
                        log.debug("Successfully converted to MemberActivityDocument: category={}", category);
                    }

                } catch (Exception e) {
                    log.error("Error processing KafkaPublisherDeadLetterDocument: id={}, category={}, error={}",
                            document.getId(), document.getCategory(), e.getMessage(), e);
                    // 처리 실패한 항목은 건너뜀
                }
            }

            log.info("Processed {} documents into {} MemberActivityDocuments",
                    documents.size(), memberActivityDocuments.size());
            return memberActivityDocuments.isEmpty() ? null : memberActivityDocuments;
        };
    }

    @Bean
    @StepScope
    public ItemWriter<List<MemberActivityDocument>> publishMemberActivityWriter() {
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
                // member_activities 컬렉션에 저장
                memberActivityMongoRepository.saveAll(allMemberActivities);
                log.info("Saved {} member activities to member_activities collection",
                        allMemberActivities.size());

                // kafka_publisher_dead_letters 문서의 isCompleted를 true로 업데이트
                List<KafkaPublisherDeadLetterDocument> documentsToUpdate = currentBatchDocuments.get();
                if (documentsToUpdate != null && !documentsToUpdate.isEmpty()) {
                    int updatedCount = 0;
                    for (KafkaPublisherDeadLetterDocument doc : documentsToUpdate) {
                        try {
                            doc.markAsCompleted();
                            kafkaPublisherDeadLetterRepository.save(doc);
                            updatedCount++;
                            log.debug("Marked as completed: id={}, category={}", doc.getId(), doc.getCategory());
                        } catch (Exception e) {
                            log.error("Failed to update isCompleted for id={}: {}",
                                    doc.getId(), e.getMessage(), e);
                        }
                    }

                    log.info("Updated {} kafka_publisher_dead_letters documents to isCompleted=true", updatedCount);
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