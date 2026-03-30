package com.shandong.policyagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.model.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话事实缓存：将关键事实结构化写入 Redis，供多轮对话复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionFactCacheService {

    public enum FactSource {
        USER_INPUT,
        WEB_SEARCH,
        ASSISTANT_RESPONSE
    }

    private static final Pattern PRICE_PATTERN = Pattern.compile("(?<!\\d)(\\d{3,6}(?:\\.\\d{1,2})?)\\s*(元|rmb|¥|￥)");
    private static final Pattern REGION_PATTERN = Pattern.compile("([\\p{IsHan}]{2,}(?:省|市|区|县))");
    private static final Pattern DEVICE_PATTERN = Pattern.compile(
            "(iphone\\s*\\d{1,2}[a-z0-9\\s+-]{0,12}|华为[\\p{IsHan}a-z0-9\\s+-]{0,12}|小米[\\p{IsHan}a-z0-9\\s+-]{0,12}|荣耀[\\p{IsHan}a-z0-9\\s+-]{0,12}|oppo[\\p{IsHan}a-z0-9\\s+-]{0,12}|vivo[\\p{IsHan}a-z0-9\\s+-]{0,12}|macbook\\s*[a-z0-9\\s+-]{0,18}|thinkpad\\s*[a-z0-9\\s+-]{0,18}|surface\\s*[a-z0-9\\s+-]{0,18})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CATEGORY_PATTERN = Pattern.compile(
            "(手机|平板|手表|手环|空调|冰箱|洗衣机|电视|热水器)"
    );
    private static final String FACT_KEY_PREFIX = "chat:facts:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.advisor.memory.ttl-days:7}")
    private long ttlDays;

    public SessionFacts mergeFacts(String conversationId, ChatRequest request) {
        SessionFacts existing = loadFacts(conversationId);
        SessionFacts merged = existing == null ? new SessionFacts() : existing;

        String message = request == null ? "" : safe(request.getMessage());
        applyTextFacts(message, merged, FactSource.USER_INPUT);

        if (request != null) {
            if (request.getCityCode() != null && !request.getCityCode().isBlank()) {
                merged.setCityCode(request.getCityCode().trim());
            }
            if (request.getLatitude() != null && request.getLongitude() != null) {
                merged.setLatitude(request.getLatitude());
                merged.setLongitude(request.getLongitude());
            }
        }

        merged.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        saveFacts(conversationId, merged);
        return merged;
    }

    public SessionFacts mergeFactsFromText(String conversationId, String text) {
        return mergeFactsFromText(conversationId, text, FactSource.ASSISTANT_RESPONSE);
    }

    public SessionFacts mergeFactsFromText(String conversationId, String text, FactSource source) {
        if (conversationId == null || conversationId.isBlank() || text == null || text.isBlank()) {
            return loadFacts(conversationId);
        }
        SessionFacts existing = loadFacts(conversationId);
        SessionFacts merged = existing == null ? new SessionFacts() : existing;
        applyTextFacts(text, merged, source == null ? FactSource.ASSISTANT_RESPONSE : source);
        merged.setUpdatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        saveFacts(conversationId, merged);
        return merged;
    }

    SessionFacts extractFactsFromText(String message) {
        SessionFacts facts = new SessionFacts();
        applyTextFacts(message, facts, FactSource.USER_INPUT);
        return facts;
    }

    public String toPromptContext(SessionFacts facts) {
        if (facts == null) {
            return "";
        }

        boolean hasContent = (facts.getLatestPrice() != null)
                || !facts.getCategories().isEmpty()
                || !facts.getRegions().isEmpty()
                || !facts.getDeviceModels().isEmpty()
                || (facts.getCityCode() != null && !facts.getCityCode().isBlank());
        if (!hasContent) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n【会话事实缓存】");
        if (!facts.getDeviceModels().isEmpty()) {
            sb.append("\n- 设备型号：").append(String.join("、", facts.getDeviceModels()));
        }
        if (!facts.getCategories().isEmpty()) {
            sb.append("\n- 商品类别：").append(String.join("、", facts.getCategories()));
        }
        if (facts.getLatestPrice() != null) {
            sb.append("\n- 最近提及金额：").append(facts.getLatestPrice()).append("元");
        }
        if (!facts.getRegions().isEmpty()) {
            sb.append("\n- 地区线索：").append(String.join("、", facts.getRegions()));
        }
        if (facts.getCityCode() != null && !facts.getCityCode().isBlank()) {
            sb.append("\n- 城市编码：").append(facts.getCityCode());
        }
        if (facts.getLatitude() != null && facts.getLongitude() != null) {
            sb.append("\n- 最近定位：lat=").append(facts.getLatitude())
                    .append(", lng=").append(facts.getLongitude());
        }
        return sb.toString();
    }

    private void saveFacts(String conversationId, SessionFacts facts) {
        String key = FACT_KEY_PREFIX + conversationId;
        try {
            String json = objectMapper.writeValueAsString(facts);
            redisTemplate.opsForValue().set(key, json, ttlDays, TimeUnit.DAYS);
        } catch (Exception exception) {
            log.warn("保存会话事实缓存失败 | conversationId={} | error={}",
                    conversationId, exception.getMessage());
        }
    }

    private SessionFacts loadFacts(String conversationId) {
        String key = FACT_KEY_PREFIX + conversationId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return new SessionFacts();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<SessionFacts>() {
            });
        } catch (Exception exception) {
            log.warn("读取会话事实缓存失败，使用空缓存 | conversationId={} | error={}",
                    conversationId, exception.getMessage());
            return new SessionFacts();
        }
    }

    private void extractPrice(String message, SessionFacts facts) {
        String normalizedMessage = normalize(message);
        Matcher matcher = PRICE_PATTERN.matcher(normalizedMessage);
        Double latest = null;
        while (matcher.find()) {
            if (isLikelySubsidyAmount(normalizedMessage, matcher.start(), matcher.end())) {
                continue;
            }
            latest = Double.parseDouble(matcher.group(1));
        }
        if (latest != null) {
            facts.setLatestPrice(latest);
        }
    }

    private void extractRegions(String message, SessionFacts facts) {
        Matcher matcher = REGION_PATTERN.matcher(message);
        while (matcher.find()) {
            String region = matcher.group(1).trim();
            if (!region.isBlank()) {
                facts.getRegions().add(region);
            }
        }
    }

    private void extractDeviceModels(String message, SessionFacts facts) {
        Matcher matcher = DEVICE_PATTERN.matcher(normalize(message));
        while (matcher.find()) {
            String model = matcher.group(1).replaceAll("\\s+", " ").trim();
            if (!model.isBlank()) {
                facts.getDeviceModels().add(model);
            }
        }
    }

    private void extractCategories(String message, SessionFacts facts) {
        Matcher matcher = CATEGORY_PATTERN.matcher(message);
        while (matcher.find()) {
            String category = matcher.group(1).trim();
            if (!category.isBlank()) {
                facts.getCategories().add(category);
            }
        }
    }

    private void applyTextFacts(String message, SessionFacts facts, FactSource source) {
        if (source == FactSource.USER_INPUT || source == FactSource.WEB_SEARCH) {
            extractPrice(message, facts);
            extractDeviceModels(message, facts);
            if (source == FactSource.USER_INPUT) {
                extractCategories(message, facts);
                extractRegions(message, facts);
            }
            inferCategoriesFromDeviceModels(facts);
            return;
        }

        if (source == FactSource.ASSISTANT_RESPONSE) {
            extractDeviceModels(message, facts);
            inferCategoriesFromDeviceModels(facts);
        }
    }

    private boolean isLikelySubsidyAmount(String normalizedMessage, int start, int end) {
        int from = Math.max(0, start - 12);
        int to = Math.min(normalizedMessage.length(), end + 12);
        String window = normalizedMessage.substring(from, to);
        return containsAny(window, "补贴", "可获补贴", "实际补贴", "补贴额度", "优惠", "减免");
    }

    private void inferCategoriesFromDeviceModels(SessionFacts facts) {
        if (facts == null || facts.getDeviceModels().isEmpty()) {
            return;
        }
        for (String model : facts.getDeviceModels()) {
            String normalized = normalize(model);
            if (containsAny(normalized, "iphone", "华为", "小米", "荣耀", "oppo", "vivo", "手机")) {
                facts.getCategories().add("手机");
            }
            if (containsAny(normalized, "ipad", "平板")) {
                facts.getCategories().add("平板");
            }
            if (containsAny(normalized, "watch", "手表", "手环")) {
                facts.getCategories().add("手表");
            }
            if (containsAny(normalized, "macbook", "thinkpad", "surface", "笔记本")) {
                facts.getCategories().add("笔记本");
            }
        }
    }

    private String normalize(String text) {
        return safe(text).toLowerCase(Locale.ROOT);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private boolean containsAny(String text, String... patterns) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String pattern : patterns) {
            if (text.contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static class SessionFacts {
        private Set<String> deviceModels = new LinkedHashSet<>();
        private Set<String> categories = new LinkedHashSet<>();
        private Set<String> regions = new LinkedHashSet<>();
        private Double latestPrice;
        private String cityCode;
        private Double latitude;
        private Double longitude;
        private String updatedAt;

        public Set<String> getDeviceModels() {
            return deviceModels;
        }

        public void setDeviceModels(Set<String> deviceModels) {
            this.deviceModels = deviceModels == null ? new LinkedHashSet<>() : deviceModels;
        }

        public Set<String> getRegions() {
            return regions;
        }

        public void setRegions(Set<String> regions) {
            this.regions = regions == null ? new LinkedHashSet<>() : regions;
        }

        public Set<String> getCategories() {
            return categories;
        }

        public void setCategories(Set<String> categories) {
            this.categories = categories == null ? new LinkedHashSet<>() : categories;
        }

        public Double getLatestPrice() {
            return latestPrice;
        }

        public void setLatestPrice(Double latestPrice) {
            this.latestPrice = latestPrice;
        }

        public String getCityCode() {
            return cityCode;
        }

        public void setCityCode(String cityCode) {
            this.cityCode = cityCode;
        }

        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
