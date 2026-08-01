package org.example.ecomm.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {
    @Bean(name = "imageUploadExecutor")
    public ExecutorService imageUploadExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
