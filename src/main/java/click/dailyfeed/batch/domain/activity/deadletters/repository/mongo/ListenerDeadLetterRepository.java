package click.dailyfeed.batch.domain.activity.deadletters.repository.mongo;

import click.dailyfeed.batch.domain.activity.deadletters.document.ListenerDeadLetterDocument;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ListenerDeadLetterRepository extends MongoRepository<ListenerDeadLetterDocument, ObjectId> {
    List<ListenerDeadLetterDocument> findByMessageKey(String messageKey);

    // isCompleted=false인 데이터를 최신순으로 조회
    List<ListenerDeadLetterDocument> findByIsCompletedOrderByCreatedAtDesc(Boolean isCompleted, Pageable pageable);
}
