package com.shandong.policyagent.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * 会话ID，用于多轮对话上下文管理
     */
    private String conversationId;

    /**
     * 用户消息
     */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /**
     * 城市代码（可选），用于过滤地市政策
     */
    private String cityCode;

    /**
     * 浏览器定位纬度（可选）
     */
    private Double latitude;

    /**
     * 浏览器定位经度（可选）
     */
    private Double longitude;

    /**
     * 浏览器定位精度（米，可选）
     */
    private Double locationAccuracy;

    /**
     * 图片Base64数据列表（可选），最多3张，每张≤5MB
     * 格式：纯base64字符串（不含data:image/...;base64,前缀）
     */
    private List<String> imageBase64List;

    /**
     * 图片格式（可选），默认jpeg，支持jpeg/png/webp
     */
    @Builder.Default
    private String imageFormat = "jpeg";

    public boolean hasImages() {
        return imageBase64List != null && !imageBase64List.isEmpty();
    }
}
