package com.shandong.policyagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 联网搜索工具专用线程池配置。
 * <p>
 * WebSearchTool 此前把 singleflight 搜索任务提交到 chatAsyncTaskExecutor 后在请求线程上
 * join 等待，而价格预取（ChatService → PricePrefetchCoordinator → ProductPriceCacheService）
 * 同样跑在 chatAsyncTaskExecutor 上——同池"提交后阻塞等待"互相嵌套，且 ThreadPoolTaskExecutor
 * 队列不满时不会扩容超过 core，2 个并发价格类提问即可全池永久死锁。故为搜索独立建池，与 chat 池彻底解耦。
 */
@Configuration
public class WebSearchExecutorConfig {

    @Bean(name = "webSearchTaskExecutor")
    public Executor webSearchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("web-search-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        // CallerRunsPolicy：池满时由调用线程直接执行，搜索任务不丢弃；
        // 同步等待路径已有 orTimeout(30s) 兜底，内联执行不会无限阻塞请求线程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
