package com.shandong.policyagent.service;

import com.shandong.policyagent.multimodal.service.VisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 图片分析服务。
 * <p>
 * 负责调用视觉模型分析用户上传的图片（单张或多张），提取设备/家电信息。
 * 从 ChatService 拆分而来；多图场景并行调用视觉模型（A10 优化），单图保持串行。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnalysisService {

    /** 视觉模型单图耗时上限，防止池被占满/死锁时 join 无限等待挂死 Tomcat 请求线程 */
    private static final long IMAGE_ANALYZE_TIMEOUT_SECONDS = 60;

    private final VisionService visionService;
    private final @Qualifier("chatAsyncTaskExecutor") Executor imageAnalysisExecutor;

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

            // A10 优化：多图并行调用视觉模型，避免耗时线性增长（按原图顺序拼接结果）
            String prompt = buildDeviceRecognitionPrompt();
            List<CompletableFuture<String>> futures = new ArrayList<>(imageBase64List.size());
            for (int i = 0; i < imageBase64List.size(); i++) {
                final int index = i;
                futures.add(CompletableFuture.supplyAsync(
                        () -> visionService.analyzeBase64Image(imageBase64List.get(index), format, prompt),
                        imageAnalysisExecutor)
                        // 超时后 join 抛 TimeoutException，由既有外层 catch 兜底（整批返回 null，与原失败语义一致）
                        .orTimeout(IMAGE_ANALYZE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < futures.size(); i++) {
                // join 抛 CompletionException（unchecked），由外层 catch 兜底，行为与原串行实现一致
                combined.append(String.format("【图片%d识别结果】\n%s\n\n", i + 1, futures.get(i).join()));
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
