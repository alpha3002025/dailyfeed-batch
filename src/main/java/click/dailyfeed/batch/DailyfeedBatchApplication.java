package click.dailyfeed.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;

@EnableBatchProcessing
@SpringBootApplication
public class DailyfeedBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DailyfeedBatchApplication.class, args);
    }

}
