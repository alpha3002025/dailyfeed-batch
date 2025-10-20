package click.dailyfeed.batch.domain.activity.deadletters.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "listener_dead_letters")
public class ListenerDeadLetterDocument {
    @Id
    private ObjectId id;
    @Field("redis_key")
    private String messageKey;
    private String payload; // jackson serialize
    @Field("is_completed")
    private Boolean isCompleted = Boolean.FALSE;
    @Field("is_editing")
    private Boolean isEditing = Boolean.FALSE;

    @Field("published_at")
    private LocalDateTime publishedAt; // 메시지 내의 created_at

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Builder(builderMethodName = "newInstanceBuilder", builderClassName = "newListenerDeadLetterDocument")
    private ListenerDeadLetterDocument(String messageKey, String payload, LocalDateTime publishedAt) {
        this.messageKey = messageKey;
        this.payload = payload;
        this.publishedAt = publishedAt;
        this.isCompleted = Boolean.FALSE;
        this.isEditing = Boolean.FALSE;
    }

    public static ListenerDeadLetterDocument newDeadLetter(String messageKey, String payload, LocalDateTime publishedAt) {
        return ListenerDeadLetterDocument.newInstanceBuilder()
                .messageKey(messageKey)
                .payload(payload)
                .publishedAt(publishedAt)
                .build();
    }

    public void markAsCompleted() {
        this.isCompleted = Boolean.TRUE;
    }
}
