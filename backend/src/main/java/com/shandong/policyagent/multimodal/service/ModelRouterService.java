package com.shandong.policyagent.multimodal.service;

import com.shandong.policyagent.model.ChatRequest;
import com.shandong.policyagent.model.ChatResponse;
import com.shandong.policyagent.multimodal.model.MultiModalRequest;
import com.shandong.policyagent.multimodal.model.MultiModalResponse;
import com.shandong.policyagent.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRouterService {

    private final ChatService chatService;
    private final AsrService asrService;
    private final VisionService visionService;

    public MultiModalResponse route(MultiModalRequest request) {
        InputType inputType = detectInputType(request);
        log.info("路由请求 | 输入类型={} | conversationId={}", inputType, request.getConversationId());

        return switch (inputType) {
            case TEXT -> handleTextInput(request);
            case AUDIO_URL -> handleAudioUrlInput(request);
            case AUDIO_BASE64 -> handleAudioBase64Input(request);
            case IMAGE_URL -> handleImageUrlInput(request);
            case IMAGE_BASE64 -> handleImageBase64Input(request);
        };
    }

    /**
     * 流式路由（已下线 ChatService.chatStream，统一走同步模式）。
     * <p>
     * 原调用 {@code chatService.chatStream()} 返回 Flux，现改为调用 {@code chatService.chat()} 同步接口，
     * 将结果包装为单元素 Flux。行为变化：不再逐 token 推送，改为整段响应一次返回。
     * </p>
     */
    public Flux<String> routeStream(MultiModalRequest request) {
        InputType inputType = detectInputType(request);

        if (inputType == InputType.TEXT) {
            ChatRequest chatRequest = ChatRequest.builder()
                    .message(request.getText())
                    .conversationId(request.getConversationId())
                    .build();
            ChatResponse chatResponse = chatService.chat(chatRequest);
            if (chatResponse.getContent() == null || chatResponse.getContent().isBlank()) {
                return Flux.empty();
            }
            return Flux.just(chatResponse.getContent());
        }

        MultiModalResponse response = route(request);
        return Flux.just(response.getContent());
    }

    private InputType detectInputType(MultiModalRequest request) {
        if (hasValue(request.getAudioUrl())) {
            return InputType.AUDIO_URL;
        }
        if (hasValue(request.getAudioBase64())) {
            return InputType.AUDIO_BASE64;
        }
        if (hasValue(request.getImageUrl())) {
            return InputType.IMAGE_URL;
        }
        if (hasValue(request.getImageBase64())) {
            return InputType.IMAGE_BASE64;
        }
        return InputType.TEXT;
    }

    private MultiModalResponse handleTextInput(MultiModalRequest request) {
        ChatRequest chatRequest = ChatRequest.builder()
                .message(request.getText())
                .conversationId(request.getConversationId())
                .build();

        ChatResponse chatResponse = chatService.chat(chatRequest);

        return MultiModalResponse.success(
                chatResponse.getContent(),
                "text",
                chatResponse.getConversationId()
        );
    }

    private MultiModalResponse handleAudioUrlInput(MultiModalRequest request) {
        try {
            String transcribedText = asrService.transcribeShortAudio(request.getAudioUrl());

            if (Boolean.TRUE.equals(request.getContinueChat())) {
                return continueWithChat(transcribedText, request.getConversationId());
            }

            return MultiModalResponse.success(transcribedText, "asr", request.getConversationId());
        } catch (Exception e) {
            log.error("音频URL处理失败", e);
            return MultiModalResponse.error("音频识别失败: " + e.getMessage());
        }
    }

    private MultiModalResponse handleAudioBase64Input(MultiModalRequest request) {
        try {
            String format = request.getAudioFormat() != null ? request.getAudioFormat() : "wav";
            String transcribedText = asrService.transcribeBase64Audio(request.getAudioBase64(), format);

            if (Boolean.TRUE.equals(request.getContinueChat())) {
                return continueWithChat(transcribedText, request.getConversationId());
            }

            return MultiModalResponse.success(transcribedText, "asr", request.getConversationId());
        } catch (Exception e) {
            log.error("Base64音频处理失败", e);
            return MultiModalResponse.error("音频识别失败: " + e.getMessage());
        }
    }

    private MultiModalResponse handleImageUrlInput(MultiModalRequest request) {
        try {
            String analysisResult;

            if ("invoice".equalsIgnoreCase(request.getImageType())) {
                analysisResult = visionService.analyzeInvoiceImage(request.getImageUrl());
            } else if ("device".equalsIgnoreCase(request.getImageType())) {
                analysisResult = visionService.analyzeDeviceImage(request.getImageUrl());
            } else {
                String question = hasValue(request.getText()) ? request.getText() : "请描述这张图片的内容";
                analysisResult = visionService.analyzeImage(request.getImageUrl(), question);
            }

            if (Boolean.TRUE.equals(request.getContinueChat())) {
                String combinedMessage = buildImageContextMessage(analysisResult, request.getText());
                return continueWithChat(combinedMessage, request.getConversationId());
            }

            return MultiModalResponse.success(analysisResult, "vision", request.getConversationId());
        } catch (Exception e) {
            log.error("图片URL处理失败", e);
            return MultiModalResponse.error("图片分析失败: " + e.getMessage());
        }
    }

    private MultiModalResponse handleImageBase64Input(MultiModalRequest request) {
        try {
            String format = request.getImageFormat() != null ? request.getImageFormat() : "png";
            String question = hasValue(request.getText()) ? request.getText() : "请描述这张图片的内容";

            String analysisResult = visionService.analyzeBase64Image(
                    request.getImageBase64(),
                    format,
                    question
            );

            if (Boolean.TRUE.equals(request.getContinueChat())) {
                String combinedMessage = buildImageContextMessage(analysisResult, request.getText());
                return continueWithChat(combinedMessage, request.getConversationId());
            }

            return MultiModalResponse.success(analysisResult, "vision", request.getConversationId());
        } catch (Exception e) {
            log.error("Base64图片处理失败", e);
            return MultiModalResponse.error("图片分析失败: " + e.getMessage());
        }
    }

    private MultiModalResponse continueWithChat(String extractedContent, String conversationId) {
        ChatRequest chatRequest = ChatRequest.builder()
                .message(extractedContent)
                .conversationId(conversationId)
                .build();

        ChatResponse chatResponse = chatService.chat(chatRequest);

        return MultiModalResponse.success(
                chatResponse.getContent(),
                "chat",
                chatResponse.getConversationId()
        );
    }

    private String buildImageContextMessage(String imageAnalysis, String userQuestion) {
        if (hasValue(userQuestion)) {
            return String.format("图片分析结果：%s\n\n用户问题：%s", imageAnalysis, userQuestion);
        }
        return "图片分析结果：" + imageAnalysis;
    }

    private boolean hasValue(String str) {
        return str != null && !str.isBlank();
    }

    private enum InputType {
        TEXT,
        AUDIO_URL,
        AUDIO_BASE64,
        IMAGE_URL,
        IMAGE_BASE64
    }
}
