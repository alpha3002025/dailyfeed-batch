package click.dailyfeed.batch.config.job.member.fixture;

import click.dailyfeed.code.domain.member.member.type.data.CountryCode;
import click.dailyfeed.code.domain.member.member.type.data.GenderType;
import click.dailyfeed.code.domain.member.member.type.data.PrivacyLevel;
import click.dailyfeed.code.domain.member.member.type.data.VerificationStatus;
import click.dailyfeed.code.domain.member.member.validator.MemberProfileValidation;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

public class MemberFixtureDto {
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SignupRequest {
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다")
        private String email;

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 50, message = "비밀번호는 8자 이상 50자 이하여야 합니다")
        @Pattern(regexp = MemberProfileValidation.PasswordValidation.PATTERN,
                message = "비밀번호는 대소문자, 숫자, 특수문자를 포함해야 합니다")
        private String password;

        @NotBlank(message = "회원명은 필수입니다")
        @Size(min = 2, max = 30, message = "회원명은 2자 이상 30자 이하여야 합니다")
        private String memberName; /// 실제 이름, 성명

        @NotBlank(message = "핸들은 필수입니다")
        @Size(min = 3, max = 50, message = "핸들은 3자 이상 50자 이하여야 합니다")
        @Pattern(regexp = MemberProfileValidation.HandleValidation.PATTERN, message = "핸들은 영문, 숫자, 언더스코어만 사용 가능합니다")
        private String handle; /// 원하는 id (문자열 기반 id)

        @Size(max = 50, message = "표시명은 50자를 초과할 수 없습니다")
        private String displayName; /// 닉네임

        @Size(max = 500, message = "소개는 500자를 초과할 수 없습니다")
        private String bio;

        @Size(max = 100, message = "위치는 100자를 초과할 수 없습니다")
        private String location;

        @URL(message = "올바른 URL 형식이 아닙니다")
        @Size(max = 200, message = "웹사이트 URL은 200자를 초과할 수 없습니다")
        private String websiteUrl;

        @Past(message = "생년월일은 과거 날짜여야 합니다")
        private LocalDate birthDate;

        @NotNull(message = "성별은 필수입니다")
        private GenderType gender;

        @NotBlank(message = "타임존은 필수입니다")
        @Builder.Default
        private String timezone = "UTC";

        @NotBlank(message = "언어 코드는 필수입니다")
        @Size(min = 2, max = 5, message = "언어 코드는 2-5자여야 합니다")
        @Builder.Default
        private String languageCode = "en";

        @NotNull(message = "국가 코드는 필수입니다")
        private CountryCode countryCode;

        @Builder.Default
        private VerificationStatus verificationStatus = VerificationStatus.NONE;

        @Builder.Default
        private PrivacyLevel privacyLevel = PrivacyLevel.PUBLIC;

        private final Integer profileCompletionScore = 0;

        @NotNull(message = "활성 상태는 필수입니다")
        @Builder.Default
        private Boolean isActive = true;

        //        @URL(message = "올바른 아바타 URL 형식이 아닙니다")
//        @Size(max = 500, message = "아바타 URL은 500자를 초과할 수 없습니다")
        private String avatarUrl;

        @URL(message = "올바른 커버 URL 형식이 아닙니다")
        @Size(max = 500, message = "커버 URL은 500자를 초과할 수 없습니다")
        private String coverUrl;
    }
}
