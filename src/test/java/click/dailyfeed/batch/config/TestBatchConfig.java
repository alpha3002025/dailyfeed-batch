package click.dailyfeed.batch.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 테스트용 최소 설정
 * - JPA와 Batch만 활성화
 * - MongoDB, Redis, Kafka 제외
 */
@Configuration
@EnableAutoConfiguration(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        RedisAutoConfiguration.class,
        KafkaAutoConfiguration.class
})
@EntityScan(basePackages = "click.dailyfeed.batch.domain.**.entity")
@EnableJpaRepositories(
        basePackages = "click.dailyfeed.batch.domain.**.repository.jpa"
)
@EnableJpaAuditing
@ComponentScan(
        basePackages = {
                "click.dailyfeed.batch.config.job.member",
                "click.dailyfeed.batch.domain.member"
        },
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = ".*Mongo.*"
                )
        }
)
public class TestBatchConfig {
}
