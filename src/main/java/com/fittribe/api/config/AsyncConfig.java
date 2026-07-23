package com.fittribe.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Bean(name = "aiInsightExecutor")
    public TaskExecutor aiInsightExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("ai-insight-");
        executor.initialize();
        return executor;
    }

    /**
     * Pool for the post-finish pipeline (PR detection + everything else
     * that can run after the HTTP response is sent). Larger than the AI
     * insight pool because the pipeline does ~10 sequential steps with
     * DB I/O and we expect higher concurrency at peak.
     *
     * CallerRunsPolicy: if the queue overflows we run the work on the
     * HTTP thread instead of dropping it. Slow finish under load is
     * acceptable; lost coins / PRs are not.
     */
    @Bean(name = "prDetectionExecutor")
    public TaskExecutor prDetectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("pr-detection-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
