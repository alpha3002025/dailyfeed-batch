package click.dailyfeed.batch.domain.redisdlq.document;

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

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "redis_dead_letters")
public class RedisDLQDocument {
    @Id
    private ObjectId id;
    @Field("message_key")
    private String messageKey;
    private String payload; // jackson serialize
    @Field("is_completed")
    private Boolean isCompleted = Boolean.FALSE;
    @Field("is_editing")
    private Boolean isEditing = Boolean.FALSE;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;
    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Builder(builderMethodName = "newRedisDLQBuilder", builderClassName = "NewRedisDLQ")
    private RedisDLQDocument(String messageKey, String payload) {
        this.messageKey = messageKey;
        this.payload = payload;
    }

    public static RedisDLQDocument newRedisDLQ(String messageKey, String payload) {
        return RedisDLQDocument.newRedisDLQBuilder()
                .messageKey(messageKey)
                .payload(payload)
                .build();
    }

    public void markAsCompleted() {
        this.isCompleted = true;
    }
}
