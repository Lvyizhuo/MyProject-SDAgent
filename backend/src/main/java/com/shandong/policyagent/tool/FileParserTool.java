package com.shandong.policyagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件解析工具 - 用于解析用户上传的发票、旧机参数表等文档
 * 支持 PDF、Word、Excel、图片等多种格式
 */
@Slf4j
@Configuration
public class FileParserTool {

    // 发票关键字段正则匹配
    private static final Pattern INVOICE_NUMBER_PATTERN = Pattern.compile("发票号码[：:](\\s*)(\\d+)");
    private static final Pattern INVOICE_DATE_PATTERN = Pattern.compile("开票日期[：:](.+?)(?:\\s|$)");
    private static final Pattern INVOICE_AMOUNT_NUMBER_PATTERN = Pattern.compile("(?:小写|¥|￥)[：:]?\\s*(\\d+(?:\\.\\d{2})?)");
    private static final Pattern SELLER_NAME_PATTERN = Pattern.compile("销售方[名称]*[：:](.+?)(?:\\s|统一社会信用代码|$)");
    private static final Pattern BUYER_NAME_PATTERN = Pattern.compile("购买方[名称]*[：:](.+?)(?:\\s|统一社会信用代码|$)");
    private static final Pattern PRODUCT_NAME_PATTERN = Pattern.compile("(?:货物或应税劳务|商品)名称[：:](.+?)(?:\\s|规格|$)");

    // 旧机参数关键字段
    private static final Pattern BRAND_PATTERN = Pattern.compile("(?:品牌|厂商)[：:]\\s*(.+?)(?:\\s|$)");
    private static final Pattern MODEL_PATTERN = Pattern.compile("(?:型号|规格)[：:]\\s*(.+?)(?:\\s|$)");
    private static final Pattern PURCHASE_DATE_PATTERN = Pattern.compile("(?:购买日期|购入时间)[：:]\\s*(.+?)(?:\\s|$)");
    private static final Pattern ORIGINAL_PRICE_PATTERN = Pattern.compile("(?:原价|购买价格|原购买价)[：:]\\s*[¥￥]?(\\d+(?:\\.\\d{2})?)");

    /**
     * 文件解析请求
     */
    public record FileParseRequest(
            String fileContent,     // Base64 编码的文件内容
            String fileName,        // 文件名（用于判断类型）
            String parseType        // 解析类型: "invoice"(发票), "device_params"(旧机参数), "auto"(自动识别)
    ) {}

    /**
     * 文件解析响应
     */
    public record FileParseResponse(
            boolean success,
            String parseType,
            String rawText,
            Map<String, String> extractedFields,
            String summary
    ) {}

    @Bean
    @Description("解析用户上传的文件，提取关键信息。支持发票（PDF/图片）和旧机参数表（Excel/Word/PDF）的解析。" +
            "输入参数：fileContent（Base64编码的文件内容）、fileName（文件名）、parseType（解析类型：invoice/device_params/auto）。" +
            "返回提取的结构化信息，如发票号码、金额、商品名称，或设备品牌、型号、购买日期等。")
    public Function<FileParseRequest, FileParseResponse> parseFile() {
        return request -> {
            log.info("解析文件 | 文件名={} | 解析类型={}", request.fileName(), request.parseType());

            try {
                // 解码 Base64 内容
                byte[] fileBytes = Base64.getDecoder().decode(request.fileContent());
                Resource resource = new ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() {
                        return request.fileName();
                    }
                };

                // 使用 Tika 读取文档
                TikaDocumentReader reader = new TikaDocumentReader(resource);
                List<Document> documents = reader.get();

                if (documents.isEmpty()) {
                    return new FileParseResponse(
                            false,
                            request.parseType(),
                            "",
                            Map.of(),
                            "无法解析文件内容，文件可能为空或格式不支持"
                    );
                }

                String rawText = documents.stream()
                        .map(Document::getText)
                        .reduce("", (a, b) -> a + "\n" + b)
                        .trim();

                // 确定解析类型
                String parseType = determineParseType(request.parseType(), rawText, request.fileName());

                // 根据类型提取字段
                Map<String, String> extractedFields = switch (parseType) {
                    case "invoice" -> extractInvoiceFields(rawText);
                    case "device_params" -> extractDeviceParams(rawText);
                    default -> extractGeneralFields(rawText);
                };

                // 生成摘要
                String summary = generateSummary(parseType, extractedFields);

                log.info("文件解析完成 | 解析类型={} | 提取字段数={}", parseType, extractedFields.size());

                return new FileParseResponse(
                        true,
                        parseType,
                        rawText.length() > 500 ? rawText.substring(0, 500) + "..." : rawText,
                        extractedFields,
                        summary
                );

            } catch (IllegalArgumentException e) {
                log.error("Base64 解码失败", e);
                return new FileParseResponse(
                        false,
                        request.parseType(),
                        "",
                        Map.of(),
                        "文件内容解码失败，请确保使用正确的 Base64 编码"
                );
            } catch (Exception e) {
                log.error("文件解析异常", e);
                return new FileParseResponse(
                        false,
                        request.parseType(),
                        "",
                        Map.of(),
                        "文件解析失败：" + e.getMessage()
                );
            }
        };
    }

    /**
     * 确定解析类型
     */
    private String determineParseType(String requestedType, String text, String fileName) {
        if (!"auto".equalsIgnoreCase(requestedType) && requestedType != null && !requestedType.isEmpty()) {
            return requestedType;
        }

        // 根据文件名判断
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.contains("发票") || lowerFileName.contains("invoice")) {
            return "invoice";
        }
        if (lowerFileName.contains("参数") || lowerFileName.contains("旧机") || lowerFileName.contains("设备")) {
            return "device_params";
        }

        // 根据内容判断
        String lowerText = text.toLowerCase();
        if (lowerText.contains("发票") || lowerText.contains("税额") || lowerText.contains("价税合计")) {
            return "invoice";
        }
        if (lowerText.contains("品牌") && (lowerText.contains("型号") || lowerText.contains("规格"))) {
            return "device_params";
        }

        return "general";
    }

    /**
     * 提取发票字段
     */
    private Map<String, String> extractInvoiceFields(String text) {
        Map<String, String> fields = new HashMap<>();

        extractField(text, INVOICE_NUMBER_PATTERN, "发票号码", fields, 2);
        extractField(text, INVOICE_DATE_PATTERN, "开票日期", fields, 1);
        extractField(text, INVOICE_AMOUNT_NUMBER_PATTERN, "金额", fields, 1);
        extractField(text, SELLER_NAME_PATTERN, "销售方", fields, 1);
        extractField(text, BUYER_NAME_PATTERN, "购买方", fields, 1);
        extractField(text, PRODUCT_NAME_PATTERN, "商品名称", fields, 1);

        return fields;
    }

    /**
     * 提取旧机参数
     */
    private Map<String, String> extractDeviceParams(String text) {
        Map<String, String> fields = new HashMap<>();

        extractField(text, BRAND_PATTERN, "品牌", fields, 1);
        extractField(text, MODEL_PATTERN, "型号", fields, 1);
        extractField(text, PURCHASE_DATE_PATTERN, "购买日期", fields, 1);
        extractField(text, ORIGINAL_PRICE_PATTERN, "原购买价格", fields, 1);

        // 尝试识别设备类型
        String lowerText = text.toLowerCase();
        if (lowerText.contains("空调")) {
            fields.put("设备类型", "空调");
        } else if (lowerText.contains("冰箱")) {
            fields.put("设备类型", "冰箱");
        } else if (lowerText.contains("洗衣机")) {
            fields.put("设备类型", "洗衣机");
        } else if (lowerText.contains("电视")) {
            fields.put("设备类型", "电视");
        } else if (lowerText.contains("手机")) {
            fields.put("设备类型", "手机");
        } else if (lowerText.contains("平板")) {
            fields.put("设备类型", "平板");
        }

        return fields;
    }

    /**
     * 提取通用字段
     */
    private Map<String, String> extractGeneralFields(String text) {
        Map<String, String> fields = new HashMap<>();

        // 尝试提取金额
        Pattern amountPattern = Pattern.compile("[¥￥](\\d+(?:\\.\\d{2})?)");
        Matcher matcher = amountPattern.matcher(text);
        if (matcher.find()) {
            fields.put("金额", matcher.group(1));
        }

        // 尝试提取日期
        Pattern datePattern = Pattern.compile("(\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}日?)");
        matcher = datePattern.matcher(text);
        if (matcher.find()) {
            fields.put("日期", matcher.group(1));
        }

        return fields;
    }

    /**
     * 辅助方法：提取单个字段
     */
    private void extractField(String text, Pattern pattern, String fieldName,
                             Map<String, String> fields, int groupIndex) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String value = matcher.group(groupIndex);
            if (value != null && !value.trim().isEmpty()) {
                fields.put(fieldName, value.trim());
            }
        }
    }

    /**
     * 生成解析摘要
     */
    private String generateSummary(String parseType, Map<String, String> fields) {
        if (fields.isEmpty()) {
            return "未能从文件中提取到结构化信息";
        }

        StringBuilder sb = new StringBuilder();
        switch (parseType) {
            case "invoice" -> {
                sb.append("【发票信息】");
                if (fields.containsKey("发票号码")) {
                    sb.append("发票号：").append(fields.get("发票号码")).append("；");
                }
                if (fields.containsKey("金额")) {
                    sb.append("金额：¥").append(fields.get("金额")).append("；");
                }
                if (fields.containsKey("商品名称")) {
                    sb.append("商品：").append(fields.get("商品名称")).append("；");
                }
                if (fields.containsKey("开票日期")) {
                    sb.append("日期：").append(fields.get("开票日期"));
                }
            }
            case "device_params" -> {
                sb.append("【旧机参数】");
                if (fields.containsKey("设备类型")) {
                    sb.append("类型：").append(fields.get("设备类型")).append("；");
                }
                if (fields.containsKey("品牌")) {
                    sb.append("品牌：").append(fields.get("品牌")).append("；");
                }
                if (fields.containsKey("型号")) {
                    sb.append("型号：").append(fields.get("型号")).append("；");
                }
                if (fields.containsKey("原购买价格")) {
                    sb.append("原价：¥").append(fields.get("原购买价格"));
                }
            }
            default -> {
                sb.append("【文件内容】提取到 ").append(fields.size()).append(" 个字段：");
                fields.forEach((k, v) -> sb.append(k).append("=").append(v).append("；"));
            }
        }

        return sb.toString();
    }
}
