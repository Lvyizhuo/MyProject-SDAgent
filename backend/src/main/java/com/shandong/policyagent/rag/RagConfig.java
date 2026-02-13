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
         * 默认切片大小（token 数）
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
         * 切片重叠 token 数
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
        private int topK = 5;

        /**
         * 相似度阈值（0-1）
         */
        private double similarityThreshold = 0.7;

        /**
         * 向量召回候选数量（重排前）
         */
        private int candidateTopK = 20;

        /**
         * 是否启用 rerank 重排序
         */
        private boolean rerankEnabled = true;

        /**
         * 重排序模型名
         */
        private String rerankModel = "qwen3-rerank";

        /**
         * 重排序接口地址
         */
        private String rerankEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

        /**
         * 发送给 rerank 的单条文档最大字符数
         */
        private int rerankMaxDocChars = 3000;
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
