package click.dailyfeed.batch.domain.activity.member.document;

import click.dailyfeed.code.domain.activity.type.MemberActivityType;
import lombok.Builder;
import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter
@Builder
@Document("member_activities")
@CompoundIndexes({
        // 1. 특정 회원의 활동 내역 조회 (최신순)
        @CompoundIndex(
                name = "idx_member_created",
                def = "{'member_id': 1, 'created_at': -1}"
        ),

        // 2. 특정 회원의 특정 타입 활동 조회 (최신순)
        @CompoundIndex(
                name = "idx_member_type_created",
                def = "{'member_id': 1, 'member_activity_type': 1, 'created_at': -1}"
        ),

        // 3. 특정 게시글에 대한 활동 조회
        @CompoundIndex(
                name = "idx_post_created",
                def = "{'post_id': 1, 'created_at': -1}"
        ),

        // 4. 특정 게시글의 특정 타입 활동 조회 (예: 좋아요 수 집계)
        @CompoundIndex(
                name = "idx_post_type",
                def = "{'post_id': 1, 'member_activity_type': 1}"
        ),

        // 5. 댓글 활동 조회용
        @CompoundIndex(
                name = "idx_comment_type",
                def = "{'comment_id': 1, 'member_activity_type': 1}",
                partialFilter = "{'comment_id': {$exists: true}}"
        )
})
public class MemberActivityDocument {
    @Id
    private ObjectId id;

    @Field("member_id")
    @Indexed
    private Long memberId;

    @Field("post_id")
    private Long postId;

    @Field("comment_id")
    private Long commentId;

    @Field("member_activity_type")
    private MemberActivityType memberActivityType;

    @Field("message_key")
    private String messageKey;

    @CreatedDate
    @Field("created_at")
    @Indexed
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    @PersistenceCreator
    public MemberActivityDocument(
            ObjectId id, Long memberId, Long postId, Long commentId, MemberActivityType memberActivityType, String messageKey, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        this.id = id;
        this.memberId = memberId;
        this.postId = postId;
        this.commentId = commentId;
        this.memberActivityType = memberActivityType;
        this.messageKey = messageKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public MemberActivityDocument(
            Long memberId, Long postId, Long commentId, MemberActivityType memberActivityType, String messageKey
    ) {
        this.memberId = memberId;
        this.postId = postId;
        this.commentId = commentId;
        this.memberActivityType = memberActivityType;
        this.messageKey = messageKey;
    }

    public static MemberActivityDocument ofNewPostActivity(Long memberId, Long postId, MemberActivityType memberActivityType){
        return new MemberActivityDocument(memberId, postId, null, memberActivityType, null);
    }

    public static MemberActivityDocument ofNewPostActivity(Long memberId, Long postId, MemberActivityType memberActivityType, String messageKey){
        return new MemberActivityDocument(memberId, postId, null, memberActivityType, messageKey);
    }

    public static MemberActivityDocument ofNewCommentActivity(Long memberId, Long postId, Long commentId, MemberActivityType memberActivityType){
        return new MemberActivityDocument(memberId, postId, commentId, memberActivityType, null);
    }

    public static MemberActivityDocument ofNewCommentActivity(Long memberId, Long postId, Long commentId, MemberActivityType memberActivityType, String messageKey){
        return new MemberActivityDocument(memberId, postId, commentId, memberActivityType, messageKey);
    }

    public static MemberActivityDocument ofNewMemberActivity(Long memberId, MemberActivityType memberActivityType){
        return new MemberActivityDocument(memberId, null, null, memberActivityType, null);
    }

    public static MemberActivityDocument ofNewMemberActivity(Long memberId, MemberActivityType memberActivityType, String messageKey){
        return new MemberActivityDocument(memberId, null, null, memberActivityType, messageKey);
    }

    public static MemberActivityDocument ofNewPostLikeActivity(Long memberId, Long postId, MemberActivityType memberActivityType){
        return new MemberActivityDocument(memberId, postId, null, memberActivityType, null);
    }

    public static MemberActivityDocument ofNewPostLikeActivity(Long memberId, Long postId, MemberActivityType memberActivityType, String messageKey){
        return new MemberActivityDocument(memberId, postId, null, memberActivityType, messageKey);
    }

    public static MemberActivityDocument ofNewCommentLikeActivity(Long memberId, Long commentId, MemberActivityType memberActivityType){
        return new MemberActivityDocument(memberId, null, commentId, memberActivityType, null);
    }

    public static MemberActivityDocument ofNewCommentLikeActivity(Long memberId, Long commentId, MemberActivityType memberActivityType, String messageKey){
        return new MemberActivityDocument(memberId, null, commentId, memberActivityType, messageKey);
    }
}
