package com.shandong.policyagent.rag;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 配置类
 * 管理文档处理和向量存储相关的配置参数
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.rag")
public class RagConfig {

    /**
     * 文档存储根目录
     */
    private String documentPath = "data";

    /**
     * 文本切片配置
     */
    private Chunking chunking = new Chunking();

    /**
     * 检索配置
     */
    private Retrieval retrieval = new Retrieval();

    /**
     * 爬虫 JSON 数据源配置
     */
    private Scraped scraped = new Scraped();

    /**
     * 增量入库配置
     */
    private Incremental incremental = new Incremental();

    /**
     * 启动加载配置
     */
    private Bootstrap bootstrap = new Bootstrap();

    @Data
    public static class Chunking {
        /**
         * 默认切片大小（字符数）
         */
        private int defaultChunkSize = 800;

        /**
         * 最小切片大小
         */
        private int minChunkSizeChars = 350;

        /**
         * 最大切片数量限制
         */
        private int minChunkLengthToEmbed = 5;

        /**
         * 切片重叠字符数
         */
        private int chunkOverlap = 100;

        /**
         * 是否保留分隔符
         */
        private boolean keepSeparator = true;

        /**
         * 小于该字符数的文档不切分，直接整体入库
         */
        private int noSplitMaxChars = 6000;
    }

    @Data
    public static class Retrieval {
        /**
         * 检索返回的最大文档数
         */
        private int topK = 2;

        /**
         * 相似度阈值（0-1）
         */
        private double similarityThreshold = 0.5;

        /**
         * 向量召回候选数量（重排前）
         */
        private int candidateTopK = 6;

        /**
         * 是否启用 rerank 重排序
         */
        private boolean rerankEnabled = true;

        /**
         * 重排序服务商标识：dashscope | xinference | tei
         */
        private String rerankProvider = "tei";

        /**
         * 重排序模型名
         */
        private String rerankModel = "bge-reranker-v2-m3";

        /**
         * 重排序接口地址
         */
        private String rerankEndpoint = "http://localhost:8989/rerank";

        /**
         * 发送给 rerank 的单条文档最大字符数
         */
        private int rerankMaxDocChars = 800;

        /**
         * 注入到最终对话提示词中的单条知识库文档最大字符数
         */
        private int promptMaxDocChars = 1200;

        /**
         * 检索结果级缓存 TTL（分钟），0 或负数表示禁用缓存。
         * 缓存键由 query + embeddingModelId + folderPath + topK + rerank 配置组合计算 SHA-256。
         */
        private int cacheTtlMinutes = 30;

        /**
         * 是否启用语义化检索缓存：按查询向量做近似匹配，让改写问法也能命中缓存。
         */
        private boolean semanticCacheEnabled = false;

        /**
         * 语义化缓存的余弦相似度阈值，低于该值视为不同问题。
         */
        private double semanticCacheThreshold = 0.92;

        /**
         * 是否在启动后预热高频政策问法的检索缓存。
         */
        private boolean warmupEnabled = false;

        /**
         * 预热延迟（秒），避免与启动期的模型加载抢资源。
         */
        private long warmupDelaySeconds = 20;
    }

    @Data
    public static class Scraped {
        /**
         * 是否启用爬虫 JSON 文档加载
         */
        private boolean enabled = true;

        /**
         * 爬虫政策 JSON 路径列表（按顺序尝试）
         */
        private List<String> policyJsonPaths = new ArrayList<>(
                List.of("scripts/data/scraped/policies.json", "data/scraped/policies.json")
        );
    }

    @Data
    public static class Incremental {
        /**
         * 是否启用增量入库
         */
        private boolean enabled = true;

        /**
         * 增量入库状态文件路径
         */
        private String stateFilePath = "data/.rag-ingestion-state.json";
    }

    @Data
    public static class Bootstrap {
        /**
         * 应用启动后是否自动触发文档入库
         */
        private boolean autoLoadOnStartup = true;
    }
}
