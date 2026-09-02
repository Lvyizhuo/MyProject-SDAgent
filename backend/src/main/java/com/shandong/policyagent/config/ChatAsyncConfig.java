package com.shandong.policyagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ChatAsyncConfig {

    @Bean(name = "chatAsyncTaskExecutor")
    public Executor chatAsyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        // 队列未满时 ThreadPoolTaskExecutor 不会扩容到 max（先入队、满了才建新线程），
        // 容量过大会让 max 形同虚设——此前 200 导致实际并发恒为 core=2，故收紧到 50
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("chat-async-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}