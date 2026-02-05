package com.shandong.policyagent.advisor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final long DEFAULT_TTL_DAYS = 7;
    private static final int DEFAULT_MAX_MESSAGES = 20;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlDays;
    private final int maxMessages;

    public RedisChatMemory(StringRedisTemplate redisTemplate) {
        this(redisTemplate, new ObjectMapper(), DEFAULT_TTL_DAYS, DEFAULT_MAX_MESSAGES);
    }

    public RedisChatMemory(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, long ttlDays, int maxMessages) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlDays = ttlDays;
        this.maxMessages = maxMessages;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = buildKey(conversationId);
        try {
            List<MessageRecord> existingRecords = getMessageRecords(key);
            for (Message message : messages) {
                existingRecords.add(toRecord(message));
            }
            // Keep only the last maxMessages
            if (existingRecords.size() > maxMessages) {
                existingRecords = existingRecords.subList(existingRecords.size() - maxMessages, existingRecords.size());
            }
            String json = objectMapper.writeValueAsString(existingRecords);
            redisTemplate.opsForValue().set(key, json, ttlDays, TimeUnit.DAYS);
            log.debug("Added {} messages to conversation {}", messages.size(), conversationId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize messages for conversation {}", conversationId, e);
            throw new RuntimeException("Failed to add messages to memory", e);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        String key = buildKey(conversationId);
        List<MessageRecord> records = getMessageRecords(key);
        
        List<Message> result = new ArrayList<>();
        for (MessageRecord record : records) {
            result.add(toMessage(record));
        }
        
        log.debug("Retrieved {} messages from conversation {}", result.size(), conversationId);
        return result;
    }

    @Override
    public void clear(String conversationId) {
        String key = buildKey(conversationId);
        redisTemplate.delete(key);
        log.debug("Cleared conversation {}", conversationId);
    }

    private String buildKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    private List<MessageRecord> getMessageRecords(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<MessageRecord>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize messages, returning empty list", e);
            return new ArrayList<>();
        }
    }

    private MessageRecord toRecord(Message message) {
        return new MessageRecord(
                message.getMessageType().name(),
                message.getText(),
                message.getMetadata()
        );
    }

    private Message toMessage(MessageRecord record) {
        MessageType type = MessageType.valueOf(record.type());
        return switch (type) {
            case USER -> new UserMessage(record.content());
            case ASSISTANT -> new AssistantMessage(record.content(), record.metadata());
            default -> new UserMessage(record.content());
        };
    }

    private record MessageRecord(String type, String content, Map<String, Object> metadata) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private StringRedisTemplate redisTemplate;
        private ObjectMapper objectMapper = new ObjectMapper();
        private long ttlDays = DEFAULT_TTL_DAYS;
        private int maxMessages = DEFAULT_MAX_MESSAGES;

        public Builder redisTemplate(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder ttlDays(long ttlDays) {
            this.ttlDays = ttlDays;
            return this;
        }

        public Builder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public RedisChatMemory build() {
            if (redisTemplate == null) {
                throw new IllegalStateException("RedisTemplate is required");
            }
            return new RedisChatMemory(redisTemplate, objectMapper, ttlDays, maxMessages);
        }
    }
}
