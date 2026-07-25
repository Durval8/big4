package com.financedash.investments.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Enables @CreatedDate/@LastModifiedDate on documents and provides the app clock. */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
