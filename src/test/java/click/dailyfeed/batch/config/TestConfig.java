package click.dailyfeed.batch.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 테스트 환경 설정
 * MongoDB, Kafka, Redis 관련 컴포넌트를 스캔에서 제외
 */
@TestConfiguration
@ComponentScan(
        basePackages = "click.dailyfeed.batch",
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "click\\.dailyfeed\\.(kafka|redis|deadletter)\\..*"
                )
        }
)
public class TestConfig {
}
