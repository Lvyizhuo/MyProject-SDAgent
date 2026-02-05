package com.shandong.policyagent.multimodal.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiModalRequest {

    private String text;
    private String conversationId;

    private String audioUrl;
    private String audioBase64;
    @Builder.Default
    private String audioFormat = "mp3";

    private String imageUrl;
    private String imageBase64;
    @Builder.Default
    private String imageFormat = "jpeg";
    private String imageType;

    @Builder.Default
    private Boolean continueChat = false;

    @Data
    public static class AudioTranscription {
        private String audioUrl;
        private String base64Audio;
        private String audioFormat = "mp3";
    }

    @Data
    public static class ImageAnalysis {
        private List<String> imageUrls;
        private String base64Image;
        private String imageFormat = "jpeg";
        @NotBlank(message = "问题不能为空")
        private String question;
    }

    @Data
    public static class InvoiceAnalysis {
        private String imageUrl;
        private String base64Image;
        private String imageFormat = "jpeg";
    }

    @Data
    public static class DeviceAnalysis {
        private String imageUrl;
        private String base64Image;
        private String imageFormat = "jpeg";
    }
}
