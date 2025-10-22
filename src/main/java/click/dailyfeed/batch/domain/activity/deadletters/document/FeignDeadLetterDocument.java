package click.dailyfeed.batch.domain.activity.deadletters.document;

import click.dailyfeed.code.domain.activity.type.MemberActivityType;
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

/**
 * Kafka 와 달리 Feign 은 중복수신 개념이 없기에, messageKey 로 중복체크가 필요없다. 그래서 messageKey 를 배제했다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "feign_dead_letters")
public class FeignDeadLetterDocument {
    @Id
    private ObjectId id;
    private String payload; // jackson serialize

    @Field("is_completed")
    private Boolean isCompleted = Boolean.FALSE;

    @Field("is_editing")
    private Boolean isEditing = Boolean.FALSE;

    @Field("category")
    private MemberActivityType.Category category;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Builder(builderMethodName = "newInstanceBuilder", builderClassName = "newListenerDeadLetterDocument")
    private FeignDeadLetterDocument(String payload, MemberActivityType.Category category) {
        this.payload = payload;
        this.isCompleted = Boolean.FALSE;
        this.isEditing = Boolean.FALSE;
        this.category = category;
    }

    public static FeignDeadLetterDocument newDeadLetter(String payload, MemberActivityType.Category category) {
        return FeignDeadLetterDocument.newInstanceBuilder()
                .payload(payload)
                .category(category)
                .build();
    }

    public void markAsCompleted() {
        this.isCompleted = Boolean.TRUE;
    }
}
