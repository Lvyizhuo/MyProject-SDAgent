package com.shandong.policyagent.multimodal.service;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.shandong.policyagent.multimodal.config.DashScopeMultiModalConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionService {

    private final DashScopeMultiModalConfig config;

    public String analyzeImage(String imageUrl, String question) {
        return analyzeImages(List.of(imageUrl), question);
    }

    public String analyzeImages(List<String> imageUrls, String question) {
        log.info("开始图像分析 | 图片数量={} | 问题={}", imageUrls.size(), question);

        try {
            MultiModalConversation conv = new MultiModalConversation();

            List<Map<String, Object>> contentList = new ArrayList<>();
            for (String imageUrl : imageUrls) {
                contentList.add(Collections.singletonMap("image", imageUrl));
            }
            contentList.add(Collections.singletonMap("text", question));

            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(contentList)
                    .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(config.getApiKey())
                    .model(config.getVision().getModel())
                    .message(userMessage)
                    .build();

            MultiModalConversationResult result = conv.call(param);

            String response = extractTextFromResult(result);
            log.info("图像分析完成 | 响应长度={}", response.length());

            return response;

        } catch (Exception e) {
            log.error("图像分析失败", e);
            throw new RuntimeException("图像分析失败: " + e.getMessage(), e);
        }
    }

    public String analyzeBase64Image(String base64Image, String imageFormat, String question) {
        log.info("开始Base64图像分析 | 格式={}", imageFormat);

        try {
            MultiModalConversation conv = new MultiModalConversation();

            String imageDataUri = String.format("data:image/%s;base64,%s", imageFormat, base64Image);

            List<Map<String, Object>> contentList = new ArrayList<>();
            contentList.add(Collections.singletonMap("image", imageDataUri));
            contentList.add(Collections.singletonMap("text", question));

            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(contentList)
                    .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(config.getApiKey())
                    .model(config.getVision().getModel())
                    .message(userMessage)
                    .build();

            MultiModalConversationResult result = conv.call(param);

            String response = extractTextFromResult(result);
            log.info("Base64图像分析完成 | 响应长度={}", response.length());

            return response;

        } catch (Exception e) {
            log.error("Base64图像分析失败", e);
            throw new RuntimeException("图像分析失败: " + e.getMessage(), e);
        }
    }

    public String analyzeInvoiceImage(String imageUrl) {
        String prompt = """
                请仔细分析这张发票图片，提取以下信息：
                1. 发票号码
                2. 开票日期
                3. 销售方名称
                4. 购买方名称
                5. 商品名称/项目
                6. 金额（小写）
                7. 税额
                8. 价税合计
                
                请以结构化的格式返回提取的信息。如果某项信息无法识别，请标注"无法识别"。
                """;
        return analyzeImage(imageUrl, prompt);
    }

    public String analyzeDeviceImage(String imageUrl) {
        String prompt = """
                请分析这张设备/电器图片，识别以下信息：
                1. 设备类型（如：空调、冰箱、洗衣机、电视、手机等）
                2. 品牌（如果可见）
                3. 型号（如果可见）
                4. 设备状态（新/旧/损坏等）
                5. 其他可识别的特征
                
                请以结构化的格式返回识别结果。如果某项信息无法识别，请说明原因。
                """;
        return analyzeImage(imageUrl, prompt);
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResult(MultiModalConversationResult result) {
        try {
            List<Map<String, Object>> content = (List<Map<String, Object>>) result.getOutput()
                    .getChoices().get(0)
                    .getMessage()
                    .getContent();

            if (content != null && !content.isEmpty()) {
                Object textObj = content.get(0).get("text");
                return textObj != null ? textObj.toString() : "";
            }
            return "";
        } catch (Exception e) {
            log.warn("提取响应文本失败", e);
            return "";
        }
    }
}
