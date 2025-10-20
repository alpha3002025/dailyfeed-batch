package click.dailyfeed.batch.domain.activity.member.repository.mongo;

import click.dailyfeed.batch.domain.activity.member.document.MemberActivityDocument;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MemberActivityMongoRepository extends MongoRepository<MemberActivityDocument, ObjectId> {

    // messageKey로 중복 체크
    boolean existsByMessageKey(String messageKey);

    // messageKey 리스트로 존재하는 messageKey들 조회
    List<MemberActivityDocument> findByMessageKeyIn(List<String> messageKeys);
}
