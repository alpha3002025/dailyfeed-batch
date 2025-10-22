package click.dailyfeed.batch.config.activity.listener.restore;

import click.dailyfeed.batch.domain.activity.member.document.MemberActivityDocument;
import click.dailyfeed.batch.domain.activity.member.repository.mongo.MemberActivityMongoTemplate;
import click.dailyfeed.code.domain.activity.transport.MemberActivityTransportDto;
import click.dailyfeed.code.domain.activity.type.MemberActivityType;
import click.dailyfeed.redis.global.deadletter.kafka.MemberActivityEventRedisService;
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
public class ListenerRedisDeadletterRestoreJobConfig {

    private final MemberActivityEventRedisService memberActivityEventRedisService;
    private final MemberActivityMongoTemplate memberActivityMongoTemplate;

    // ThreadLocal로 현재 처리 중인 MemberActivityMessage 들을 추적 (롤백용)
    private static final ThreadLocal<List<MemberActivityTransportDto.MemberActivityMessage>> currentBatchMessages =
            new ThreadLocal<>();

    public ListenerRedisDeadletterRestoreJobConfig(
            MemberActivityEventRedisService memberActivityEventRedisService,
            MemberActivityMongoTemplate memberActivityMongoTemplate) {
        this.memberActivityEventRedisService = memberActivityEventRedisService;
        this.memberActivityMongoTemplate = memberActivityMongoTemplate;
    }

    @Bean
    public Job listenerRedisDeadletterRestoreJob(
            JobRepository jobRepository,
            Step listenerRedisDeadletterRestoreStep) {
        return new JobBuilder("listenerRedisDeadletterRestoreJob", jobRepository)
                .start(listenerRedisDeadletterRestoreStep)
                .build();
    }

    @Bean
    public Step listenerRedisDeadletterRestoreStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<List<MemberActivityTransportDto.MemberActivityMessage>> redisDeadLetterReader,
            ItemProcessor<List<MemberActivityTransportDto.MemberActivityMessage>, List<MemberActivityDocument>> redisDeadLetterProcessor,
            ItemWriter<List<MemberActivityDocument>> redisDeadLetterWriter) {
        return new StepBuilder("listenerRedisDeadletterRestoreStep", jobRepository)
                .<List<MemberActivityTransportDto.MemberActivityMessage>, List<MemberActivityDocument>>chunk(1, transactionManager)
                .reader(redisDeadLetterReader)
                .processor(redisDeadLetterProcessor)
                .writer(redisDeadLetterWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<List<MemberActivityTransportDto.MemberActivityMessage>> redisDeadLetterReader() {
        return new ItemReader<List<MemberActivityTransportDto.MemberActivityMessage>>() {
            private static final int BATCH_SIZE = 50;
            private boolean hasMore = true;

            @Override
            public List<MemberActivityTransportDto.MemberActivityMessage> read() {
                if (!hasMore) {
                    return null;
                }

                try {
                    log.debug("Reading batch from Redis dead letter queue");

                    // Redis에서 dead letter 메시지 읽기
                    List<MemberActivityTransportDto.MemberActivityMessage> messages =
                            memberActivityEventRedisService.lPopTopNDeadLetter(BATCH_SIZE);

                    if (messages != null && !messages.isEmpty()) {
                        log.info("Fetched {} messages from Redis dead letter queue", messages.size());

                        // ThreadLocal에 저장 (롤백용)
                        currentBatchMessages.set(new ArrayList<>(messages));

                        // 50건 미만이면 다음 읽기에서 null 반환
                        if (messages.size() < BATCH_SIZE) {
                            hasMore = false;
                        }

                        return messages;
                    } else {
                        log.info("No more messages in Redis dead letter queue");
                        hasMore = false;
                        return null;
                    }
                } catch (Exception e) {
                    log.error("Error reading from Redis dead letter queue", e);
                    hasMore = false;
                    return null;
                }
            }
        };
    }

    @Bean
    @StepScope
    public ItemProcessor<List<MemberActivityTransportDto.MemberActivityMessage>, List<MemberActivityDocument>> redisDeadLetterProcessor() {
        return messages -> {
            if (messages == null || messages.isEmpty()) {
                log.debug("Empty message list, skipping processing");
                return null;
            }

            List<MemberActivityDocument> memberActivityDocuments = new ArrayList<>();

            for (MemberActivityTransportDto.MemberActivityMessage message : messages) {
                try {
                    MemberActivityTransportDto.MemberActivityEvent event = message.getEvent();

                    log.debug("Processing MemberActivityMessage: key={}, memberActivityType={}",
                            message.getKey(), event.getMemberActivityType());

                    MemberActivityDocument memberActivityDocument = null;
                    MemberActivityType activityType = event.getMemberActivityType();

                    // postEventTypes에 포함되는 경우
                    if (MemberActivityType.postEventTypes.contains(activityType)) {
                        memberActivityDocument = MemberActivityDocument.ofNewPostActivity(
                                event.getMemberId(),
                                event.getPostId(),
                                activityType,
                                message.getKey()
                        );
                        log.debug("Converted to PostActivity: memberId={}, postId={}",
                                event.getMemberId(), event.getPostId());
                    }
                    // commentEventTypes에 포함되는 경우
                    else if (MemberActivityType.commentEventTypes.contains(activityType)) {
                        memberActivityDocument = MemberActivityDocument.ofNewCommentActivity(
                                event.getMemberId(),
                                event.getPostId(),
                                event.getCommentId(),
                                activityType,
                                message.getKey()
                        );
                        log.debug("Converted to CommentActivity: memberId={}, postId={}, commentId={}",
                                event.getMemberId(), event.getPostId(), event.getCommentId());
                    }
                    // postLikeEventTypes에 포함되는 경우
                    else if (MemberActivityType.postLikeEventTypes.contains(activityType)) {
                        memberActivityDocument = MemberActivityDocument.ofNewPostLikeActivity(
                                event.getMemberId(),
                                event.getPostId(),
                                activityType,
                                message.getKey()
                        );
                        log.debug("Converted to PostLikeActivity: memberId={}, postId={}",
                                event.getMemberId(), event.getPostId());
                    }
                    // commentLikeEventTypes에 포함되는 경우
                    else if (MemberActivityType.commentLikeEventTypes.contains(activityType)) {
                        memberActivityDocument = MemberActivityDocument.ofNewCommentLikeActivity(
                                event.getMemberId(),
                                event.getCommentId(),
                                activityType,
                                message.getKey()
                        );
                        log.debug("Converted to CommentLikeActivity: memberId={}, commentId={}",
                                event.getMemberId(), event.getCommentId());
                    }
                    else {
                        log.warn("Unknown or unsupported activityType: {}", activityType);
                        continue;
                    }

                    if (memberActivityDocument != null) {
                        memberActivityDocuments.add(memberActivityDocument);
                        log.debug("Successfully converted to MemberActivityDocument: activityType={}, messageKey={}",
                                activityType, message.getKey());
                    }

                } catch (Exception e) {
                    log.error("Error processing MemberActivityMessage: key={}, error={}",
                            message.getKey(), e.getMessage(), e);
                    // 처리 실패한 항목은 건너뜀
                }
            }

            log.info("Processed {} messages into {} MemberActivityDocuments",
                    messages.size(), memberActivityDocuments.size());
            return memberActivityDocuments.isEmpty() ? null : memberActivityDocuments;
        };
    }

    @Bean
    @StepScope
    public ItemWriter<List<MemberActivityDocument>> redisDeadLetterWriter() {
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
                currentBatchMessages.remove();
                return;
            }

            try {
                // MemberActivityMongoTemplate의 upsertMemberActivity 메서드 사용
                // messageKey를 통한 중복 방지
                for (MemberActivityDocument document : allMemberActivities) {
                    memberActivityMongoTemplate.upsertMemberActivity(document);
                }

                log.info("Saved {} member activities to member_activities collection using upsert",
                        allMemberActivities.size());

                // 성공 시 ThreadLocal 정리
                currentBatchMessages.remove();

            } catch (Exception e) {
                log.error("Failed to save member activities. Rolling back to Redis dead letter queue.", e);

                // 에러 발생 시 Redis로 다시 push
                List<MemberActivityTransportDto.MemberActivityMessage> messagesToRollback = currentBatchMessages.get();
                if (messagesToRollback != null && !messagesToRollback.isEmpty()) {
                    try {
                        for (MemberActivityTransportDto.MemberActivityMessage message : messagesToRollback) {
                            memberActivityEventRedisService.rPushDeadletter(message);
                            log.debug("Rolled back message to Redis: key={}", message.getKey());
                        }
                        log.info("Rolled back {} messages to Redis dead letter queue", messagesToRollback.size());
                    } catch (Exception rollbackException) {
                        log.error("Failed to rollback messages to Redis: {}", rollbackException.getMessage(), rollbackException);
                    }
                }

                // ThreadLocal 정리
                currentBatchMessages.remove();

                // 예외를 다시 던져서 Spring Batch가 실패를 인지하도록 함
                throw e;
            }
        };
    }
}