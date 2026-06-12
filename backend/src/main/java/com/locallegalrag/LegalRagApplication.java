package com.locallegalrag;

import com.locallegalrag.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class LegalRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegalRagApplication.class, args);
    }
}
