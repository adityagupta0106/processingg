package com.serviceplus.form.validation.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AssociatedTaskConfig {

    @Bean("associatedTaskExecutor")
    public Executor associatedTaskExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ASSOCIATED-TASK-");

        executor.initialize();

        return executor;
    }

}
