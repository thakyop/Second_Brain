package com.Second_Brain.Second_Brain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // No executor bean here: @Async methods run on Spring Boot's auto-configured
    // "applicationTaskExecutor", a pool of 8 threads by default
    // (spring.task.execution.pool.core-size).
}
