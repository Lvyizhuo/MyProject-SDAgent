package com.shandong.policyagent.entity;

/**
 * 模型类型枚举
 */
public enum ModelType {
    /**
     * 大语言模型 - 对应智能体对话能力
     */
    LLM,

    /**
     * 视觉模型 - 对应图像分析能力
     */
    VISION,

    /**
     * 语音模型 - 对应语音识别能力
     */
    AUDIO,

    /**
     * 嵌入模型 - 对应向量嵌入能力
     */
    EMBEDDING
}