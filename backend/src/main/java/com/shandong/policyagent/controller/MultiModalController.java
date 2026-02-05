package com.shandong.policyagent.controller;

import com.shandong.policyagent.multimodal.model.MultiModalRequest;
import com.shandong.policyagent.multimodal.model.MultiModalResponse;
import com.shandong.policyagent.multimodal.service.AsrService;
import com.shandong.policyagent.multimodal.service.VisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/multimodal")
@RequiredArgsConstructor
public class MultiModalController {

    private final AsrService asrService;
    private final VisionService visionService;

    @PostMapping("/transcribe")
    public ResponseEntity<MultiModalResponse> transcribeAudio(
            @RequestBody MultiModalRequest.AudioTranscription request) {
        log.info("收到语音识别请求");

        try {
            String result;
            if (request.getAudioUrl() != null && !request.getAudioUrl().isEmpty()) {
                result = asrService.transcribeShortAudio(request.getAudioUrl());
            } else if (request.getBase64Audio() != null && !request.getBase64Audio().isEmpty()) {
                result = asrService.transcribeBase64Audio(request.getBase64Audio(), request.getAudioFormat());
            } else {
                return ResponseEntity.badRequest()
                        .body(MultiModalResponse.error("asr", "请提供 audioUrl 或 base64Audio"));
            }

            return ResponseEntity.ok(MultiModalResponse.success("asr", result));

        } catch (Exception e) {
            log.error("语音识别失败", e);
            return ResponseEntity.internalServerError()
                    .body(MultiModalResponse.error("asr", e.getMessage()));
        }
    }

    @PostMapping("/analyze-image")
    public ResponseEntity<MultiModalResponse> analyzeImage(
            @Valid @RequestBody MultiModalRequest.ImageAnalysis request) {
        log.info("收到图像分析请求");

        try {
            String result;
            if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
                result = visionService.analyzeImages(request.getImageUrls(), request.getQuestion());
            } else if (request.getBase64Image() != null && !request.getBase64Image().isEmpty()) {
                result = visionService.analyzeBase64Image(
                        request.getBase64Image(), 
                        request.getImageFormat(), 
                        request.getQuestion());
            } else {
                return ResponseEntity.badRequest()
                        .body(MultiModalResponse.error("vision", "请提供 imageUrls 或 base64Image"));
            }

            return ResponseEntity.ok(MultiModalResponse.success("vision", result));

        } catch (Exception e) {
            log.error("图像分析失败", e);
            return ResponseEntity.internalServerError()
                    .body(MultiModalResponse.error("vision", e.getMessage()));
        }
    }

    @PostMapping("/analyze-invoice")
    public ResponseEntity<MultiModalResponse> analyzeInvoice(
            @RequestBody MultiModalRequest.InvoiceAnalysis request) {
        log.info("收到发票识别请求");

        try {
            String result;
            if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
                result = visionService.analyzeInvoiceImage(request.getImageUrl());
            } else if (request.getBase64Image() != null && !request.getBase64Image().isEmpty()) {
                result = visionService.analyzeBase64Image(
                        request.getBase64Image(),
                        request.getImageFormat(),
                        buildInvoicePrompt());
            } else {
                return ResponseEntity.badRequest()
                        .body(MultiModalResponse.error("invoice", "请提供 imageUrl 或 base64Image"));
            }

            return ResponseEntity.ok(MultiModalResponse.success("invoice", result));

        } catch (Exception e) {
            log.error("发票识别失败", e);
            return ResponseEntity.internalServerError()
                    .body(MultiModalResponse.error("invoice", e.getMessage()));
        }
    }

    @PostMapping("/analyze-device")
    public ResponseEntity<MultiModalResponse> analyzeDevice(
            @RequestBody MultiModalRequest.DeviceAnalysis request) {
        log.info("收到设备识别请求");

        try {
            String result;
            if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
                result = visionService.analyzeDeviceImage(request.getImageUrl());
            } else if (request.getBase64Image() != null && !request.getBase64Image().isEmpty()) {
                result = visionService.analyzeBase64Image(
                        request.getBase64Image(),
                        request.getImageFormat(),
                        buildDevicePrompt());
            } else {
                return ResponseEntity.badRequest()
                        .body(MultiModalResponse.error("device", "请提供 imageUrl 或 base64Image"));
            }

            return ResponseEntity.ok(MultiModalResponse.success("device", result));

        } catch (Exception e) {
            log.error("设备识别失败", e);
            return ResponseEntity.internalServerError()
                    .body(MultiModalResponse.error("device", e.getMessage()));
        }
    }

    private String buildInvoicePrompt() {
        return """
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
    }

    private String buildDevicePrompt() {
        return """
                请分析这张设备/电器图片，识别以下信息：
                1. 设备类型（如：空调、冰箱、洗衣机、电视、手机等）
                2. 品牌（如果可见）
                3. 型号（如果可见）
                4. 设备状态（新/旧/损坏等）
                5. 其他可识别的特征
                
                请以结构化的格式返回识别结果。如果某项信息无法识别，请说明原因。
                """;
    }
}
