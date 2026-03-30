package com.shandong.policyagent.advisor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "chat:memory:";
        private static final String SUMMARY_KEY_PREFIX = "chat:memory:summary:";
    private static final long DEFAULT_TTL_DAYS = 7;
    private static final int DEFAULT_MAX_MESSAGES = 20;
    private static final int DEFAULT_MAX_MESSAGE_CHARS = 2000;
        private static final boolean DEFAULT_SUMMARY_ENABLED = true;
        private static final int DEFAULT_SUMMARY_TRIGGER_MESSAGES = 8;
        private static final int DEFAULT_SUMMARY_KEEP_MESSAGES = 4;
        private static final int DEFAULT_SUMMARY_MAX_CHARS = 1200;
        private static final int DEFAULT_SUMMARY_TIMEOUT_SECONDS = 8;
        private static final String SUMMARY_SYSTEM_PROMPT = """
            你是对话记忆压缩助手。请把历史多轮对话压缩为简短摘要，要求：
            1. 保留用户关键事实：设备型号、预算/价格、地区、诉求、约束条件。
            2. 保留已确认结论，剔除寒暄和重复表述。
            3. 不要编造不存在的信息。
            4. 输出中文纯文本，最多 8 条，每条尽量简短。
            """;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlDays;
    private final int maxMessages;
    private final int maxMessageChars;
    private final boolean summaryEnabled;
    private final int summaryTriggerMessages;
    private final int summaryKeepMessages;
    private final int summaryMaxChars;
    private final int summaryTimeoutSeconds;
    private final ChatModel chatModel;
    private final Executor summaryExecutor;
    private final Map<String, CompletableFuture<Void>> summarizationTasks = new ConcurrentHashMap<>();

    public RedisChatMemory(StringRedisTemplate redisTemplate) {
        this(
                redisTemplate,
                new ObjectMapper(),
                DEFAULT_TTL_DAYS,
                DEFAULT_MAX_MESSAGES,
                DEFAULT_MAX_MESSAGE_CHARS,
                DEFAULT_SUMMARY_ENABLED,
                DEFAULT_SUMMARY_TRIGGER_MESSAGES,
                DEFAULT_SUMMARY_KEEP_MESSAGES,
                DEFAULT_SUMMARY_MAX_CHARS,
                DEFAULT_SUMMARY_TIMEOUT_SECONDS,
                null,
                Runnable::run
        );
    }

    public RedisChatMemory(StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper,
                           long ttlDays,
                           int maxMessages,
                           int maxMessageChars,
                           boolean summaryEnabled,
                           int summaryTriggerMessages,
                           int summaryKeepMessages,
                           int summaryMaxChars,
                           int summaryTimeoutSeconds,
                           ChatModel chatModel,
                           Executor summaryExecutor) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlDays = ttlDays;
        this.maxMessages = maxMessages;
        this.maxMessageChars = maxMessageChars;
        this.summaryEnabled = summaryEnabled;
        this.summaryTriggerMessages = Math.max(4, summaryTriggerMessages);
        this.summaryKeepMessages = Math.max(2, summaryKeepMessages);
        this.summaryMaxChars = Math.max(300, summaryMaxChars);
        this.summaryTimeoutSeconds = Math.max(3, summaryTimeoutSeconds);
        this.chatModel = chatModel;
        this.summaryExecutor = summaryExecutor == null ? Runnable::run : summaryExecutor;
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
            saveMessageRecords(key, existingRecords);
            triggerSummaryIfNeeded(conversationId, existingRecords.size());
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
        String summary = getSummaryContent(conversationId);
        if (summary != null && !summary.isBlank()) {
            result.add(new SystemMessage("【历史对话摘要】\n" + summary));
        }
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
        redisTemplate.delete(buildSummaryKey(conversationId));
        log.debug("Cleared conversation {}", conversationId);
    }

    private String buildKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    private String buildSummaryKey(String conversationId) {
        return SUMMARY_KEY_PREFIX + conversationId;
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
                truncate(message.getText()),
                message.getMetadata()
        );
    }

    private String truncate(String content) {
        if (content == null || content.length() <= maxMessageChars) {
            return content;
        }
        return content.substring(0, maxMessageChars) + "\n...[历史消息已截断]";
    }

    private Message toMessage(MessageRecord record) {
        MessageType type = MessageType.valueOf(record.type());
        return switch (type) {
            case USER -> new UserMessage(record.content());
            case ASSISTANT -> new AssistantMessage(record.content(), record.metadata());
            case SYSTEM -> new SystemMessage(record.content());
            default -> new UserMessage(record.content());
        };
    }

    private void saveMessageRecords(String key, List<MessageRecord> records) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(records);
        redisTemplate.opsForValue().set(key, json, ttlDays, TimeUnit.DAYS);
    }

    private void triggerSummaryIfNeeded(String conversationId, int currentMessageCount) {
        if (!summaryEnabled || chatModel == null || currentMessageCount < summaryTriggerMessages) {
            return;
        }
        if (summarizationTasks.containsKey(conversationId)) {
            return;
        }

        CompletableFuture<Void> task = CompletableFuture.runAsync(
                () -> summarizeConversation(conversationId),
                summaryExecutor
        ).orTimeout(summaryTimeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.debug("Conversation summary task failed | conversationId={} | error={}", conversationId, ex.getMessage());
                    return null;
                })
                .whenComplete((unused, throwable) -> summarizationTasks.remove(conversationId));

        summarizationTasks.put(conversationId, task);
    }

    private void summarizeConversation(String conversationId) {
        String key = buildKey(conversationId);
        List<MessageRecord> latestRecords = getMessageRecords(key);
        if (latestRecords.size() <= summaryKeepMessages + 1) {
            return;
        }

        int splitIndex = Math.max(1, latestRecords.size() - summaryKeepMessages);
        List<MessageRecord> toSummarize = latestRecords.subList(0, splitIndex);
        List<MessageRecord> toKeep = latestRecords.subList(splitIndex, latestRecords.size());

        String existingSummary = getSummaryContent(conversationId);
        String summaryInput = buildSummaryInput(existingSummary, toSummarize);
        if (summaryInput.isBlank()) {
            return;
        }

        String summary = generateSummary(summaryInput);
        if (summary == null || summary.isBlank()) {
            summary = truncate(summaryInput);
        }

        redisTemplate.opsForValue().set(buildSummaryKey(conversationId), summary, ttlDays, TimeUnit.DAYS);

        try {
            saveMessageRecords(key, toKeep);
        } catch (JsonProcessingException e) {
            log.warn("Failed to save trimmed messages after summary | conversationId={} | error={}",
                    conversationId, e.getMessage());
        }

        log.debug("Conversation summarized | conversationId={} | summarizedMessages={} | keptMessages={}",
                conversationId, toSummarize.size(), toKeep.size());
    }

    private String getSummaryContent(String conversationId) {
        String value = redisTemplate.opsForValue().get(buildSummaryKey(conversationId));
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String buildSummaryInput(String existingSummary, List<MessageRecord> records) {
        StringBuilder builder = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            builder.append("历史摘要：\n").append(existingSummary).append("\n\n");
        }
        builder.append("新增对话：\n");
        records.forEach(record -> builder
            .append(roleLabel(record.type()))
            .append("：")
            .append(record.content() == null ? "" : record.content())
            .append("\n"));
        return builder.toString().trim();
    }

    private String roleLabel(String type) {
        if (type == null) {
            return "用户";
        }
        return switch (type) {
            case "USER" -> "用户";
            case "ASSISTANT" -> "助手";
            case "SYSTEM" -> "系统";
            default -> "用户";
        };
    }

    private String generateSummary(String input) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SUMMARY_SYSTEM_PROMPT),
                new UserMessage(input)
        ));
        String content = chatModel.call(prompt).getResult().getOutput().getText();
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        if (normalized.length() <= summaryMaxChars) {
            return normalized;
        }
        return normalized.substring(0, summaryMaxChars).trim();
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
        private int maxMessageChars = DEFAULT_MAX_MESSAGE_CHARS;
        private boolean summaryEnabled = DEFAULT_SUMMARY_ENABLED;
        private int summaryTriggerMessages = DEFAULT_SUMMARY_TRIGGER_MESSAGES;
        private int summaryKeepMessages = DEFAULT_SUMMARY_KEEP_MESSAGES;
        private int summaryMaxChars = DEFAULT_SUMMARY_MAX_CHARS;
        private int summaryTimeoutSeconds = DEFAULT_SUMMARY_TIMEOUT_SECONDS;
        private ChatModel chatModel;
        private Executor summaryExecutor = Runnable::run;

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

        public Builder maxMessageChars(int maxMessageChars) {
            this.maxMessageChars = maxMessageChars;
            return this;
        }

        public Builder summaryEnabled(boolean summaryEnabled) {
            this.summaryEnabled = summaryEnabled;
            return this;
        }

        public Builder summaryTriggerMessages(int summaryTriggerMessages) {
            this.summaryTriggerMessages = summaryTriggerMessages;
            return this;
        }

        public Builder summaryKeepMessages(int summaryKeepMessages) {
            this.summaryKeepMessages = summaryKeepMessages;
            return this;
        }

        public Builder summaryMaxChars(int summaryMaxChars) {
            this.summaryMaxChars = summaryMaxChars;
            return this;
        }

        public Builder summaryTimeoutSeconds(int summaryTimeoutSeconds) {
            this.summaryTimeoutSeconds = summaryTimeoutSeconds;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder summaryExecutor(Executor summaryExecutor) {
            this.summaryExecutor = summaryExecutor;
            return this;
        }

        public RedisChatMemory build() {
            if (redisTemplate == null) {
                throw new IllegalStateException("RedisTemplate is required");
            }
            return new RedisChatMemory(
                    redisTemplate,
                    objectMapper,
                    ttlDays,
                    maxMessages,
                    maxMessageChars,
                    summaryEnabled,
                    summaryTriggerMessages,
                    summaryKeepMessages,
                    summaryMaxChars,
                    summaryTimeoutSeconds,
                    chatModel,
                    summaryExecutor
            );
        }
    }
}
