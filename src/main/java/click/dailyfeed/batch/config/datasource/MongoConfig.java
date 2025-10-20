package click.dailyfeed.batch.config.datasource;

import click.dailyfeed.batch.config.converter.BigDecimalToDecimal128Converter;
import click.dailyfeed.batch.config.converter.Decimal128ToBigDecimalConverter;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${infrastructure.mongodb.timeline.database}")
    private String database;

    @Bean
    public MongoClient mongoClient(){
        try {
            ConnectionString connectionString = new ConnectionString(mongoUri);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .build();
            return MongoClients.create(settings);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MongoDB client", e);
        }
    }

    @Bean
    public MongoTransactionManager transactionManager(
            MongoDatabaseFactory dbFactory
    ){
        return new MongoTransactionManager(dbFactory);
    }

    @Bean
    public SimpleMongoClientDatabaseFactory dailyfeedMongoDatabaseFactory(
            MongoClient mongoClient
    ){
        return new SimpleMongoClientDatabaseFactory(mongoClient, database);
    }

    @Bean(name = "mongoTemplate")
    public MongoTemplate mongoTemplate(
            MongoDatabaseFactory dailyfeedMongoDatabaseFactory,
            MongoConverter mongoConverter
    ){
        return new MongoTemplate(dailyfeedMongoDatabaseFactory, mongoConverter);
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions(){
        return new MongoCustomConversions(
                Arrays.asList(
                        new BigDecimalToDecimal128Converter(),
                        new Decimal128ToBigDecimalConverter(),
                        new LocalDateTimeToDateConverter(),
                        new DateToLocalDateTimeConverter()
                )
        );
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(
            MongoDatabaseFactory databaseFactory,
            MongoMappingContext mongoMappingContext,
            MongoCustomConversions mongoCustomConversions
    ){
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(databaseFactory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mongoMappingContext);
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        converter.setCustomConversions(mongoCustomConversions);
        converter.afterPropertiesSet();
        return converter;
    }

    private static class LocalDateTimeToDateConverter
            implements Converter<LocalDateTime, Date> {

        @Override
        public Date convert(LocalDateTime source) {
            return Timestamp.valueOf(source.plusHours(9));
        }
    }

    private static class DateToLocalDateTimeConverter
            implements Converter<Date, LocalDateTime> {

        @Override
        public LocalDateTime convert(Date source) {
            return source.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().minusHours(9);
        }
    }
}
