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

    private static final Pattern PRICE_PATTERN = Pattern.compile("(?<!\\d)(\\d{3,6}(?:\\.\\d{1,2})?)\\s*(元|rmb|¥|￥)");
    private static final Pattern REGION_PATTERN = Pattern.compile("([\\p{IsHan}]{2,}(?:省|市|区|县))");
    private static final Pattern DEVICE_PATTERN = Pattern.compile(
            "(iphone\\s*\\d{1,2}[a-z0-9\\s+-]{0,12}|华为[\\p{IsHan}a-z0-9\\s+-]{0,12}|小米[\\p{IsHan}a-z0-9\\s+-]{0,12}|荣耀[\\p{IsHan}a-z0-9\\s+-]{0,12}|oppo[\\p{IsHan}a-z0-9\\s+-]{0,12}|vivo[\\p{IsHan}a-z0-9\\s+-]{0,12})",
            Pattern.CASE_INSENSITIVE
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
        applyTextFacts(message, merged);

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

    SessionFacts extractFactsFromText(String message) {
        SessionFacts facts = new SessionFacts();
        applyTextFacts(message, facts);
        return facts;
    }

    public String toPromptContext(SessionFacts facts) {
        if (facts == null) {
            return "";
        }

        boolean hasContent = (facts.getLatestPrice() != null)
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
        if (facts.getLatestPrice() != null) {
            sb.append("\n- 最近提及价格：").append(facts.getLatestPrice()).append("元");
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
        Matcher matcher = PRICE_PATTERN.matcher(normalize(message));
        Double latest = null;
        while (matcher.find()) {
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

    private void applyTextFacts(String message, SessionFacts facts) {
        extractPrice(message, facts);
        extractRegions(message, facts);
        extractDeviceModels(message, facts);
    }

    private String normalize(String text) {
        return safe(text).toLowerCase(Locale.ROOT);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    public static class SessionFacts {
        private Set<String> deviceModels = new LinkedHashSet<>();
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
