package com.shandong.policyagent.rag;

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
    }
}
