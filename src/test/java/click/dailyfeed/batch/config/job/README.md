# Batch Job Configuration 테스트

배치 Job Configuration에 대한 통합 테스트입니다.

## 📋 테스트 대상

### 1. JwtKeyRotationJobConfig
JWT 서명 키 로테이션 배치 작업 테스트

**테스트 케이스:**
- ✅ Job이 성공적으로 실행되는지 확인
- ✅ Job 이름이 올바르게 설정되었는지 확인
- ✅ Service 예외 발생 시 Job 실패 처리
- ✅ Step이 정상적으로 실행되는지 확인
- ✅ Incrementer 동작 확인 (같은 파라미터로 여러 번 실행)

### 2. JwtKeyInitJobConfig
JWT 키 초기화 배치 작업 테스트

**테스트 케이스:**
- ✅ Job이 성공적으로 실행되는지 확인
- ✅ Job 이름이 올바르게 설정되었는지 확인
- ✅ Service 예외 발생 시 Job 실패 처리
- ✅ Step이 정상적으로 실행되는지 확인
- ✅ Incrementer 동작 확인
- ✅ 중복 Primary Key 정리 및 새 키 생성 확인

### 3. TokenCleanupJobConfig
만료된 토큰 정리 배치 작업 테스트

**테스트 케이스:**
- ✅ Job이 성공적으로 실행되는지 확인
- ✅ Job 이름이 올바르게 설정되었는지 확인
- ✅ Service 예외 발생 시 Job 실패 처리
- ✅ Step이 정상적으로 실행되는지 확인
- ✅ Incrementer 동작 확인
- ✅ RefreshToken 및 TokenBlacklist 정리 확인
- ✅ 트랜잭션 롤백 시 정리가 되지 않는지 확인

## 🛠 테스트 환경 설정

### Dependencies
```gradle
dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.batch:spring-batch-test'
    testImplementation 'com.h2database:h2'
}
```

### Test Profile (application-test.yml)
- **Database**: H2 In-Memory Database (MySQL Mode)
- **JPA**: create-drop (테스트마다 스키마 재생성)
- **Batch**: Job 자동 실행 비활성화
- **JWT Key Rotation**: 1시간 (테스트용)
- **Grace Period**: 2시간 (테스트용)

## 🚀 테스트 실행

### 전체 테스트 실행
```bash
# Gradle을 이용한 전체 테스트
./gradlew :dailyfeed-batch:test

# 특정 패키지 테스트
./gradlew :dailyfeed-batch:test --tests "click.dailyfeed.batch.config.job.*"
```

### 개별 테스트 실행
```bash
# JWT Key Rotation Job 테스트
./gradlew :dailyfeed-batch:test --tests "JwtKeyRotationJobConfigTest"

# JWT Key Init Job 테스트
./gradlew :dailyfeed-batch:test --tests "JwtKeyInitJobConfigTest"

# Token Cleanup Job 테스트
./gradlew :dailyfeed-batch:test --tests "TokenCleanupJobConfigTest"
```

### IDE에서 실행
- IntelliJ IDEA: 테스트 클래스 또는 메서드에서 `Ctrl + Shift + R` (Mac: `Cmd + Shift + R`)
- 전체 테스트: 패키지 우클릭 → "Run Tests in ..."

## 📊 테스트 커버리지

```bash
# 테스트 커버리지 리포트 생성
./gradlew :dailyfeed-batch:test jacocoTestReport

# 리포트 확인
open dailyfeed-batch/build/reports/jacoco/test/html/index.html
```

## 🔍 테스트 구조

### @SpringBatchTest
Spring Batch 테스트를 위한 어노테이션으로 다음을 자동 설정:
- `JobLauncherTestUtils`: Job 실행을 위한 유틸리티
- `JobRepositoryTestUtils`: JobRepository 테스트 유틸리티

### @MockBean
Service 계층을 Mock으로 대체하여 Job Configuration만 테스트:
- `JwtKeyRotationService`
- `JwtKeyInitService`
- `TokenCleanupService`

### @ActiveProfiles("test")
테스트 프로필 활성화로 H2 Database 및 테스트 설정 사용

## 📝 테스트 작성 가이드

### 새로운 Job 테스트 추가 시

1. **테스트 클래스 생성**
```java
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Your Job 테스트")
class YourJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @MockBean
    private YourService yourService;

    @Autowired
    private Job yourJob;
}
```

2. **기본 테스트 케이스 작성**
- Job 성공 실행 테스트
- Job 이름 확인 테스트
- 예외 처리 테스트
- Step 실행 테스트
- Incrementer 동작 테스트

3. **비즈니스 로직 테스트**
- Job의 핵심 기능에 대한 검증

## 🐛 트러블슈팅

### 테스트 실패 시 확인사항

1. **H2 Database 초기화 문제**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # 테스트마다 재생성
```

2. **Batch Job 자동 실행 방지**
```yaml
spring:
  batch:
    job:
      enabled: false  # 테스트 시 자동 실행 방지
```

3. **Service Mock 확인**
```java
// Service가 제대로 Mock되었는지 확인
verify(yourService, times(1)).yourMethod();
```

## 📚 참고 자료

- [Spring Batch Testing](https://docs.spring.io/spring-batch/docs/current/reference/html/testing.html)
- [Spring Boot Test](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [AssertJ Documentation](https://assertj.github.io/doc/)
