package com.shandong.policyagent.rag;

import com.shandong.policyagent.agent.DynamicAgentConfigHolder;
import com.shandong.policyagent.support.ChatMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 检索缓存预热。
 *
 * <p>检索管线的成本几乎全部在 rerank（CPU 交叉编码），缓存未命中时每轮都要付一次。
 * 政策咨询类问题的问法高度集中，把高频问法在启动后预热进缓存，
 * 可以让这些问题的首轮请求也走缓存路径。
 *
 * <p>预热放在后台线程 + 延迟执行，不阻塞应用启动。
 */
@Slf4j
@Component
public class RetrievalCacheWarmup implements ApplicationRunner {

    /** 高频政策问法（覆盖补贴标准、申领条件、流程、材料、车型等主要意图）。 */
    private static final List<String> WARMUP_QUERIES = List.of(
            "山东省汽车以旧换新补贴政策是什么",
            "山东省汽车以旧换新补贴标准",
            "山东省家电以旧换新补贴标准",
            "山东省数码产品以旧换新补贴",
            "以旧换新需要什么条件才能申领",
            "以旧换新需要哪些材料",
            "以旧换新补贴怎么申请",
            "以旧换新申请流程是什么",
            "补贴什么时候到账",
            "新能源汽车置换更新补贴是多少",
            "汽车报废更新补贴标准",
            "山东省以旧换新政策最新规定"
    );

    private final HybridRetrievalPipeline hybridRetrievalPipeline;
    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;
    private final RagConfig ragConfig;
    private final ChatMetrics chatMetrics;
    private final Executor chatAsyncTaskExecutor;

    /**
     * 显式构造器。
     *
     * <p>项目里有多个 Executor 类型的 Bean，{@code @Qualifier} 必须写在构造参数上；
     * 写在字段上时 Lombok 生成的构造参数不携带该注解，Spring 无法消歧义。</p>
     */
    public RetrievalCacheWarmup(HybridRetrievalPipeline hybridRetrievalPipeline,
                                DynamicAgentConfigHolder dynamicAgentConfigHolder,
                                RagConfig ragConfig,
                                ChatMetrics chatMetrics,
                                @Qualifier("chatAsyncTaskExecutor") Executor chatAsyncTaskExecutor) {
        this.hybridRetrievalPipeline = hybridRetrievalPipeline;
        this.dynamicAgentConfigHolder = dynamicAgentConfigHolder;
        this.ragConfig = ragConfig;
        this.chatMetrics = chatMetrics;
        this.chatAsyncTaskExecutor = chatAsyncTaskExecutor;
    }

    @Value("${app.rag.retrieval.warmup-enabled:false}")
    private boolean warmupEnabled;

    @Value("${app.rag.retrieval.warmup-delay-seconds:20}")
    private long warmupDelaySeconds;

    @Override
    public void run(ApplicationArguments args) {
        if (!warmupEnabled) {
            return;
        }
        try {
            chatAsyncTaskExecutor.execute(() -> {
                try {
                    Thread.sleep(warmupDelaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                warmup();
            });
        } catch (Exception e) {
            log.warn("检索缓存预热任务提交失败 | error={}", e.getMessage());
        }
    }

    private void warmup() {
        if (!ragConfig.getRetrieval().isSemanticCacheEnabled()
                && ragConfig.getRetrieval().getCacheTtlMinutes() <= 0) {
            return;
        }

        int warmed = 0;
        long start = System.currentTimeMillis();
        for (String query : WARMUP_QUERIES) {
            try {
                HybridRetrievalPipeline.RetrievalContext context =
                        hybridRetrievalPipeline.resolveRetrievalContext();
                if (context == null || context.missing()) {
                    log.warn("检索缓存预热跳过：知识库范围未配置");
                    return;
                }
                // retrieve() 内部会写入缓存（精确键 + 语义索引）
                hybridRetrievalPipeline.retrieve(query,
                        ragConfig.getRetrieval().getTopK(),
                        ragConfig.getRetrieval().getSimilarityThreshold(),
                        context);
                warmed++;
            } catch (Exception e) {
                log.debug("检索缓存预热单条失败 | query={} | error={}", query, e.getMessage());
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("检索缓存预热完成 | 问法数={} | 耗时={}ms", warmed, elapsed);
        if (warmed > 0) {
            chatMetrics.recordCache("warmup");
        }
    }
}
