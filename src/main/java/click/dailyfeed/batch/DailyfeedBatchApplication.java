package click.dailyfeed.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@EnableMongoAuditing
@EnableMongoRepositories(
        basePackages = {
                "click.dailyfeed.batch.domain.**.repository.mongo",
                "click.dailyfeed.deadletter.domain.**.repository.mongo"
        },
        mongoTemplateRef = "mongoTemplate"
)
@EnableTransactionManagement
@SpringBootApplication
@ComponentScan(basePackages = {
        "click.dailyfeed.batch",
        "click.dailyfeed.redis",
        "click.dailyfeed.kafka",
        "click.dailyfeed.deadletter",
})
@EnableJpaRepositories(
        basePackages = "click.dailyfeed.batch.domain.**.repository.jpa"
)
@EnableJpaAuditing
public class DailyfeedBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DailyfeedBatchApplication.class, args);
    }

}
