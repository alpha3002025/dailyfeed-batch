package click.dailyfeed.batch.domain.activity.member.mapper;

import click.dailyfeed.batch.domain.activity.member.document.MemberActivityDocument;
import click.dailyfeed.code.domain.activity.exception.UndefinedMemberActivityEventTypeException;
import click.dailyfeed.code.domain.activity.transport.MemberActivityTransportDto;
import click.dailyfeed.code.domain.activity.type.MemberActivityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@RequiredArgsConstructor
public class MemberActivityMapper {
    private final ObjectMapper objectMapper;

    public MemberActivityTransportDto.MemberActivityMessage fromLogFilePayload(File jsonLog) throws Exception {
        return objectMapper.readValue(jsonLog, MemberActivityTransportDto.MemberActivityMessage.class);
    }

    public MemberActivityDocument fromEvent(MemberActivityTransportDto.MemberActivityEvent event) {
        return fromEvent(event, null);
    }

    public MemberActivityDocument fromEvent(MemberActivityTransportDto.MemberActivityEvent event, String messageKey) {
        MemberActivityType memberActivityType = event.getMemberActivityType();
        if (MemberActivityType.postEventTypes.contains(memberActivityType)) {
            return MemberActivityDocument.ofNewPostActivity(
                    event.getMemberId(),
                    event.getPostId(),
                    event.getMemberActivityType(),
                    messageKey
            );

        } else if (MemberActivityType.commentEventTypes.contains(memberActivityType)) {
            return MemberActivityDocument.ofNewCommentActivity(
                    event.getMemberId(),
                    event.getPostId(),
                    event.getCommentId(),
                    event.getMemberActivityType(),
                    messageKey
            );
        } else if (MemberActivityType.memberEventTypes.contains(memberActivityType)) {
            return MemberActivityDocument.ofNewMemberActivity(
                    event.getMemberId(),
                    memberActivityType,
                    messageKey
            );
        } else if (MemberActivityType.postLikeEventTypes.contains(memberActivityType)) {
            return MemberActivityDocument.ofNewPostLikeActivity(
                    event.getMemberId(),
                    event.getPostId(),
                    memberActivityType,
                    messageKey
            );
        } else if (MemberActivityType.commentLikeEventTypes.contains(memberActivityType)) {
            return MemberActivityDocument.ofNewCommentLikeActivity(
                    event.getMemberId(),
                    event.getCommentId(),
                    memberActivityType,
                    messageKey
            );
        }

        throw new UndefinedMemberActivityEventTypeException();
    }
}
