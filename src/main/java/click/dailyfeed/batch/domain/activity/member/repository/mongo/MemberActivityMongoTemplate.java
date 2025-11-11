package click.dailyfeed.batch.domain.activity.member.repository.mongo;

import click.dailyfeed.batch.domain.activity.member.document.MemberActivityDocument;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Component
@Profile("!test")
public class MemberActivityMongoTemplate {

    private final MongoTemplate mongoTemplate;

    /**
     * MemberActivityDocument를 저장합니다.
     * - messageKey가 있는 경우: messageKey로 조회하여 upsert (중복 방지)
     * - messageKey가 없는 경우: 단순 insert
     *
     * @param document 저장할 MemberActivityDocument
     * @return upsert 결과 (messageKey가 있는 경우에만 의미 있음)
     */
    public UpdateResult upsertMemberActivity(MemberActivityDocument document) {
        if (document.getMessageKey() != null && !document.getMessageKey().isEmpty()) {
            // messageKey가 있는 경우: upsert로 중복 방지
            return upsertByMessageKey(document);
        } else {
            // messageKey가 없는 경우: 단순 insert
            mongoTemplate.insert(document);
            log.debug("Inserted MemberActivityDocument without messageKey: memberId={}, activityType={}",
                    document.getMemberId(), document.getMemberActivityType());
            return null;
        }
    }

    /**
     * messageKey로 조회하여 upsert를 수행합니다.
     * 동일한 messageKey가 있으면 업데이트, 없으면 삽입합니다.
     *
     * @param document 저장할 MemberActivityDocument
     * @return UpdateResult (matched count, modified count, upserted id 포함)
     */
    private UpdateResult upsertByMessageKey(MemberActivityDocument document) {
        Query query = new Query(Criteria.where("message_key").is(document.getMessageKey()));

        Update update = new Update()
                .set("member_id", document.getMemberId())
                .set("post_id", document.getPostId())
                .set("comment_id", document.getCommentId())
                .set("member_activity_type", document.getMemberActivityType())
                .set("message_key", document.getMessageKey())
                .setOnInsert("created_at", LocalDateTime.now())
                .set("updated_at", LocalDateTime.now());

        UpdateResult result = mongoTemplate.upsert(query, update, MemberActivityDocument.class);

        log.debug("Upserted MemberActivityDocument: messageKey={}, matched={}, modified={}, upsertedId={}",
                document.getMessageKey(),
                result.getMatchedCount(),
                result.getModifiedCount(),
                result.getUpsertedId());

        return result;
    }

    /**
     * 여러 MemberActivityDocument를 일괄 저장합니다.
     *
     * @param documents 저장할 MemberActivityDocument 리스트
     */
    public void upsertAll(Iterable<MemberActivityDocument> documents) {
        for (MemberActivityDocument document : documents) {
            upsertMemberActivity(document);
        }
    }
}
