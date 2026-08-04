package com.shandong.policyagent.service;

import com.shandong.policyagent.multimodal.service.VisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图片分析服务。
 * <p>
 * 负责调用视觉模型分析用户上传的图片（单张或多张），提取设备/家电信息。
 * 从 ChatService 拆分而来，保持原逻辑行为完全不变。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnalysisService {

    private final VisionService visionService;

    /**
     * 分析用户上传的 Base64 图片列表，提取设备/家电结构化信息。
     *
     * @param imageBase64List Base64 编码的图片数据列表
     * @param imageFormat     图片格式（jpeg/png/webp）
     * @return 结构化识别结果文本，失败时返回 null
     */
    public String analyzeImages(List<String> imageBase64List, String imageFormat) {
        String format = (imageFormat != null && !imageFormat.isBlank()) ? imageFormat : "jpeg";

        try {
            if (imageBase64List.size() == 1) {
                String result = visionService.analyzeBase64Image(
                        imageBase64List.get(0), format, buildDeviceRecognitionPrompt());
                log.info("单张图片识别完成");
                return result;
            }

            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < imageBase64List.size(); i++) {
                String result = visionService.analyzeBase64Image(
                        imageBase64List.get(i), format, buildDeviceRecognitionPrompt());
                combined.append(String.format("【图片%d识别结果】\n%s\n\n", i + 1, result));
            }
            log.info("多张图片识别完成 | 数量={}", imageBase64List.size());
            return combined.toString().trim();

        } catch (Exception e) {
            log.error("图片识别异常", e);
            return null;
        }
    }

    /**
     * 构建设备识别用的提示词模板。
     */
    public String buildDeviceRecognitionPrompt() {
        return """
                请分析这张图片中的设备/家电，提取以下信息并以结构化格式返回：
                - 设备类型（如：空调、冰箱、洗衣机、电视、热水器、手机、平板等）
                - 品牌（如果可见）
                - 型号（如果可见）
                - 设备状态（新品/旧机/损坏等）
                - 能效等级（如果可见）
                - 其他可识别的特征

                如果某项信息无法识别，请标注"未识别"。""";
    }
}
