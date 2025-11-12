package click.dailyfeed.batch.config.job.member.fixture;

import click.dailyfeed.batch.config.job.incrementer.RequestedAtSimpleIncrementer;
import click.dailyfeed.code.domain.member.member.type.data.CountryCode;
import click.dailyfeed.code.domain.member.member.type.data.GenderType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Random;

// 8889
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MemberInsertAtLocalK8sUsingNodePortJobConfig {

    private static final String IMAGE_SERVICE_BASE_URL = "http://localhost:8889";
    private static final String MEMBER_SERVICE_BASE_URL = "http://localhost:8888";
    private static final String IMAGE_UPLOAD_ENDPOINT = "/api/images/upload/profile";
    private static final String SIGNUP_ENDPOINT = "/api/authentication/signup";
    private static final String SAMPLE_IMAGES_DIR = "src/main/resources/fixture/member/images";
    private static final String OLD_IMAGE_URL_PREFIX = "http://dailyfeed.local:8889";
    private static final String NEW_IMAGE_URL_PREFIX = "http://dailyfeed.local:8889/api/images/view/";
    private static final int MIN_IMAGE_NUMBER = 1;
    private static final int MAX_IMAGE_NUMBER = 47;

    // Create a dedicated ObjectMapper with default camelCase naming strategy for API calls
    private final ObjectMapper apiObjectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final ObjectMapper objectMapper; // For image response parsing (snake_case)
    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    @Bean
    public Job memberInsertAtLocalK8sUsingNodePortJob(
            JobRepository jobRepository,
            Step memberInsertAtLocalK8sUsingNodePortStep) {
        return new JobBuilder("memberInsertAtLocalK8sUsingNodePortJob", jobRepository)
                .incrementer(new RequestedAtSimpleIncrementer())
                .start(memberInsertAtLocalK8sUsingNodePortStep)
                .build();
    }

    @Bean
    public Step memberInsertAtLocalK8sUsingNodePortStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<CsvRow> csvReader,
            ItemProcessor<CsvRow, MemberFixtureDto.SignupRequest> memberSignupProcessor,
            ItemWriter<MemberFixtureDto.SignupRequest> memberSignupWriter) {
        return new StepBuilder("memberInsertAtLocalK8sUsingNodePortStep", jobRepository)
                .<CsvRow, MemberFixtureDto.SignupRequest>chunk(10, transactionManager)
                .reader(csvReader)
                .processor(memberSignupProcessor)
                .writer(memberSignupWriter)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<CsvRow> csvReader() {
        FlatFileItemReader<CsvRow> reader = new FlatFileItemReader<>();
        reader.setResource(new ClassPathResource("fixture/member/signup_request_ai_k8s.csv"));
        reader.setLinesToSkip(1); // Skip header
        reader.setLineMapper(new DefaultLineMapper<>() {{
            setLineTokenizer(new DelimitedLineTokenizer() {{
                setNames("email", "password", "memberName", "handle", "displayName",
                        "bio", "location", "websiteUrl", "birthDate", "gender", "avataUrl");
                setDelimiter(",");
            }});
            setFieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                setTargetType(CsvRow.class);
            }});
        }});
        return reader;
    }

    @Bean
    @StepScope
    public ItemProcessor<CsvRow, MemberFixtureDto.SignupRequest> memberSignupProcessor() {
        return csvRow -> {
            try {
                String avatarUrl = csvRow.getAvataUrl();

                // Upload image if avatarUrl starts with the old image service URL pattern
                if (avatarUrl != null && avatarUrl.startsWith(OLD_IMAGE_URL_PREFIX)) {
                    int randomImageNumber = MIN_IMAGE_NUMBER + random.nextInt(MAX_IMAGE_NUMBER - MIN_IMAGE_NUMBER + 1);
                    String uploadedViewId = uploadRandomImage(randomImageNumber);
                    if (uploadedViewId != null) {
                        avatarUrl = NEW_IMAGE_URL_PREFIX + uploadedViewId;
                        log.info("🔄 Replaced avatarUrl for {} with random image #{} -> viewId: {}",
                                csvRow.getHandle(), randomImageNumber, uploadedViewId);
                    } else {
                        log.warn("⚠️  Failed to upload image for {}, keeping original URL", csvRow.getHandle());
                    }
                }

                // Convert CSV row to SignupRequest
                return toSignupRequest(csvRow, avatarUrl);
            } catch (Exception e) {
                log.error("❌ Error processing row for handle: {}", csvRow.getHandle(), e);
                throw e;
            }
        };
    }

    @Bean
    @StepScope
    public ItemWriter<MemberFixtureDto.SignupRequest> memberSignupWriter() {
        return items -> {
            for (MemberFixtureDto.SignupRequest signupRequest : items) {
                try {
                    String requestBody = apiObjectMapper.writeValueAsString(signupRequest);
                    log.info("📝 Processing signup for: {}", signupRequest.getHandle());
                    log.debug("Request JSON: {}", requestBody);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

                    ResponseEntity<String> response = restTemplate.postForEntity(
                            MEMBER_SERVICE_BASE_URL + SIGNUP_ENDPOINT,
                            requestEntity,
                            String.class
                    );

                    if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode().is2xxSuccessful()) {
                        log.info("✅ Signup successful for: {} - Response: {}",
                                signupRequest.getHandle(), response.getBody());
                    } else {
                        log.error("❌ Signup failed for: {} - Status: {}, Response: {}",
                                signupRequest.getHandle(), response.getStatusCode(), response.getBody());
                    }
                } catch (Exception e) {
                    log.error("❌ Exception while signing up: {}", signupRequest.getHandle(), e);
                    throw e;
                }
            }
        };
    }

    /**
     * Upload random image to image service
     */
    private String uploadRandomImage(int imageNumber) {
        String fileName = imageNumber + ".png";
        Path imagePath = Paths.get(SAMPLE_IMAGES_DIR, fileName);
        File imageFile = imagePath.toFile();

        if (!imageFile.exists()) {
            log.error("⚠️  Image file not found: {}", imageFile.getAbsolutePath());
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new FileSystemResource(imageFile));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    IMAGE_SERVICE_BASE_URL + IMAGE_UPLOAD_ENDPOINT,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String responseBody = response.getBody();
                JsonNode responseJson = objectMapper.readTree(responseBody);

                String viewId = extractViewId(responseJson);

                if (viewId != null && !viewId.isEmpty()) {
                    log.info("✅ Uploaded: {} -> viewId: {}", fileName, viewId);
                    return viewId;
                } else {
                    log.error("⚠️  No viewId found in response for: {}", fileName);
                    log.error("Response: {}", responseBody);
                    return null;
                }
            } else {
                log.error("❌ Failed to upload: {} - Status: {}", fileName, response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Exception while uploading: {}", fileName, e);
            return null;
        }
    }

    /**
     * Extract viewId from JSON response
     */
    private String extractViewId(JsonNode responseJson) {
        String viewId = null;

        // Try path: data (direct string)
        if (responseJson.has("data")) {
            JsonNode dataNode = responseJson.get("data");
            if (dataNode.isTextual()) {
                viewId = dataNode.asText();
            } else if (dataNode.isObject() && dataNode.has("viewId")) {
                viewId = dataNode.get("viewId").asText();
            }
        }

        // Try path: viewId (direct)
        if ((viewId == null || viewId.isEmpty()) && responseJson.has("viewId")) {
            viewId = responseJson.get("viewId").asText();
        }

        // Try path: result.viewId
        if ((viewId == null || viewId.isEmpty()) && responseJson.has("result")) {
            JsonNode resultNode = responseJson.get("result");
            if (resultNode.isObject() && resultNode.has("viewId")) {
                viewId = resultNode.get("viewId").asText();
            }
        }

        return viewId;
    }

    /**
     * Convert CSV row to SignupRequest
     */
    private MemberFixtureDto.SignupRequest toSignupRequest(CsvRow csvRow, String avatarUrl) {
        return MemberFixtureDto.SignupRequest.builder()
                .email(csvRow.getEmail())
                .password(csvRow.getPassword())
                .memberName(csvRow.getMemberName())
                .handle(csvRow.getHandle())
                .displayName(csvRow.getDisplayName())
                .bio(csvRow.getBio())
                .location(csvRow.getLocation())
                .websiteUrl(csvRow.getWebsiteUrl())
                .birthDate(parseBirthDate(csvRow.getBirthDate()))
                .gender(parseGender(csvRow.getGender()))
                .avatarUrl(avatarUrl)
                .countryCode(CountryCode.KR)
                .languageCode("en")
                .timezone("Asia/Seoul")
                .isActive(true)
                .build();
    }

    /**
     * Parse birth date string to LocalDate
     */
    private LocalDate parseBirthDate(String birthDateStr) {
        if (birthDateStr == null || birthDateStr.trim().isEmpty()) {
            return null;
        }

        try {
            // Convert "55.1.1." format to "55-1-1"
            String cleaned = birthDateStr.replace(".", "-").replaceAll("-+$", "");
            String[] parts = cleaned.split("-");

            if (parts.length >= 3) {
                int year = Integer.parseInt(parts[0]);
                // Convert 2-digit year to 4-digit (55 -> 1955, 25 -> 2025)
                if (year < 100) {
                    year = year < 30 ? 2000 + year : 1900 + year;
                }
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);

                return LocalDate.of(year, month, day);
            }
        } catch (Exception e) {
            log.error("⚠️  Failed to parse birthDate: {}", birthDateStr, e);
        }
        return null;
    }

    /**
     * Parse gender string to GenderType
     */
    private GenderType parseGender(String gender) {
        if (gender == null || gender.trim().isEmpty()) {
            return GenderType.PREFER_NOT_TO_SAY;
        }
        try {
            return GenderType.valueOf(gender);
        } catch (Exception e) {
            log.error("⚠️  Failed to parse gender: {}", gender, e);
            return GenderType.PREFER_NOT_TO_SAY;
        }
    }

    /**
     * CSV Row DTO
     */
    @Getter
    @Setter
    public static class CsvRow {
        private String email;
        private String password;
        private String memberName;
        private String handle;
        private String displayName;
        private String bio;
        private String location;
        private String websiteUrl;
        private String birthDate;
        private String gender;
        private String avataUrl;
    }
}
