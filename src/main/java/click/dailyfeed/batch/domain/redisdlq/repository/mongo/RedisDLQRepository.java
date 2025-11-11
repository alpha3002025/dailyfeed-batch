package click.dailyfeed.batch.domain.redisdlq.repository.mongo;

import click.dailyfeed.batch.domain.redisdlq.document.RedisDLQDocument;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface RedisDLQRepository extends MongoRepository<RedisDLQDocument, ObjectId> {
    List<RedisDLQDocument> findByMessageKey(String messageKey);

    // isCompleted=false AND isEditing=false 문서 조회
    List<RedisDLQDocument> findByIsCompletedFalseAndIsEditingFalse();

    // isCompleted=false AND createdAt < cutoffTime (5시간 이상 경과한 문서 조회)
    List<RedisDLQDocument> findByIsCompletedFalseAndCreatedAtBefore(Instant cutoffTime);
}
