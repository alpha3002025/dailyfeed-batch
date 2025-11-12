package click.dailyfeed.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@EnableTransactionManagement
@SpringBootApplication
@ComponentScan(basePackages = {
        "click.dailyfeed.batch",
        "click.dailyfeed.code",
        "click.dailyfeed.deadletter",
        "click.dailyfeed.redis"
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
