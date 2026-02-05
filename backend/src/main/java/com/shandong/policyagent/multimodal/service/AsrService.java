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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsrService {

    private final DashScopeMultiModalConfig config;

    public String transcribeShortAudio(String audioUrl) {
        log.info("开始语音识别 | 音频URL={}", audioUrl);

        try {
            MultiModalConversation conv = new MultiModalConversation();

            MultiModalMessage systemMessage = MultiModalMessage.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(List.of(Collections.singletonMap("text", "")))
                    .build();

            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(List.of(Collections.singletonMap("audio", audioUrl)))
                    .build();

            Map<String, Object> asrOptions = new HashMap<>();
            asrOptions.put("enable_itn", config.getAsr().isEnableItn());
            asrOptions.put("language", config.getAsr().getLanguage());

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(config.getApiKey())
                    .model(config.getAsr().getModel())
                    .message(systemMessage)
                    .message(userMessage)
                    .parameter("asr_options", asrOptions)
                    .build();

            MultiModalConversationResult result = conv.call(param);

            String text = extractTextFromResult(result);
            log.info("语音识别完成 | 识别文本长度={}", text.length());

            return text;

        } catch (Exception e) {
            log.error("语音识别失败", e);
            throw new RuntimeException("语音识别失败: " + e.getMessage(), e);
        }
    }

    public String transcribeBase64Audio(String base64Audio, String audioFormat) {
        log.info("开始Base64语音识别 | 格式={}", audioFormat);

        try {
            MultiModalConversation conv = new MultiModalConversation();

            String audioDataUri = String.format("data:audio/%s;base64,%s", audioFormat, base64Audio);

            MultiModalMessage systemMessage = MultiModalMessage.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(List.of(Collections.singletonMap("text", "")))
                    .build();

            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(List.of(Collections.singletonMap("audio", audioDataUri)))
                    .build();

            Map<String, Object> asrOptions = new HashMap<>();
            asrOptions.put("enable_itn", config.getAsr().isEnableItn());
            asrOptions.put("language", config.getAsr().getLanguage());

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(config.getApiKey())
                    .model(config.getAsr().getModel())
                    .message(systemMessage)
                    .message(userMessage)
                    .parameter("asr_options", asrOptions)
                    .build();

            MultiModalConversationResult result = conv.call(param);

            String text = extractTextFromResult(result);
            log.info("Base64语音识别完成 | 识别文本长度={}", text.length());

            return text;

        } catch (Exception e) {
            log.error("Base64语音识别失败", e);
            throw new RuntimeException("语音识别失败: " + e.getMessage(), e);
        }
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
            log.warn("提取识别文本失败", e);
            return "";
        }
    }
}
