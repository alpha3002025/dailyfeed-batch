package click.dailyfeed.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableScheduling
@EnableMongoAuditing
@EnableMongoRepositories(
        basePackages = "click.dailyfeed.batch.domain.**.repository.mongo",
        mongoTemplateRef = "mongoTemplate"
)
@EnableTransactionManagement
@SpringBootApplication
@ComponentScan(basePackages = {
        "click.dailyfeed.batch",
        "click.dailyfeed.redis",
        "click.dailyfeed.kafka",
})
public class DailyfeedBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DailyfeedBatchApplication.class, args);
    }

}
