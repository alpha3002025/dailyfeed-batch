package click.dailyfeed.batch.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 외부 의존성 패키지 스캔 설정
 * MongoDB, Kafka, Redis, Deadletter 패키지를 스캔
 * test 프로필에서는 제외됨
 */
@Configuration
@Profile("!test")
@ComponentScan(basePackages = {
        "click.dailyfeed.redis",
        "click.dailyfeed.kafka",
        "click.dailyfeed.deadletter"
})
public class ExternalDependenciesConfig {
}
