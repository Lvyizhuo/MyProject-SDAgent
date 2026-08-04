package com.shandong.policyagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ReAct 规划专用线程池配置。
 * <p>
 * 规划 LLM 调用最长阻塞 15 秒（planning-timeout-seconds），在高并发下若复用公共
 * ForkJoinPool 会 starve 其他并行任务。此池独立隔离规划任务，避免影响系统其他组件。
 * <p>
 * 与 chatAsyncTaskExecutor 的区别：
 * chatAsyncTaskExecutor 服务于 memory summary / 语义缓存写入 / 价格预取等轻量后台任务；
 * 规划任务阻塞时间长、偶发高并发，混合使用会导致队头阻塞——故独立建池。
 */
@Configuration
public class AgentPlannerExecutorConfig {

    @Bean(name = "agentPlannerTaskExecutor")
    public Executor agentPlannerTaskExecutor(
            @Value("${app.agent.planner-pool.core-size:2}") int coreSize,
            @Value("${app.agent.planner-pool.max-size:4}") int maxSize,
            @Value("${app.agent.planner-pool.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("agent-planner-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        // CallerRunsPolicy：池满时由调用线程（Tomcat 请求线程）直接执行，规划任务不丢失；
        // 代价是单次请求延迟增加，优于丢弃任务后走兜底计划。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
