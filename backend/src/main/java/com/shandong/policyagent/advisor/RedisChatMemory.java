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
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "chat:memory:";
        private static final String SUMMARY_KEY_PREFIX = "chat:memory:summary:";
    private static final long DEFAULT_TTL_DAYS = 7;
    private static final int DEFAULT_MAX_MESSAGES = 20;
    private static final int DEFAULT_MAX_MESSAGE_CHARS = 2000;
        private static final boolean DEFAULT_SUMMARY_ENABLED = true;
        private static final int DEFAULT_SUMMARY_TRIGGER_MESSAGES = 6;
        private static final int DEFAULT_SUMMARY_KEEP_MESSAGES = 2;
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
        List<MessageRecord> existingRecords = getMessageRecords(key);
        for (Message message : messages) {
            existingRecords.add(toRecord(message));
        }
        // Keep only the last maxMessages
        if (existingRecords.size() > maxMessages) {
            existingRecords = existingRecords.subList(existingRecords.size() - maxMessages, existingRecords.size());
        }
        // saveMessageRecords 内部已吞掉序列化/Redis 异常并降级记 warn，保证写入失败不阻断对话
        saveMessageRecords(key, existingRecords);
        triggerSummaryIfNeeded(conversationId, existingRecords.size());
        log.debug("Added {} messages to conversation {}", messages.size(), conversationId);
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
        try {
            redisTemplate.delete(buildKey(conversationId));
            redisTemplate.delete(buildSummaryKey(conversationId));
        } catch (Exception e) {
            // Redis 故障静默降级：清理失败记 warn 不抛出
            log.warn("清空对话记忆失败 | conversationId={} | error={}", conversationId, e.getMessage());
        }
        log.debug("Cleared conversation {}", conversationId);
    }

    private String buildKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    private String buildSummaryKey(String conversationId) {
        return SUMMARY_KEY_PREFIX + conversationId;
    }

    private List<MessageRecord> getMessageRecords(String key) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // Redis 故障静默降级：读取失败按空列表处理，不阻断对话
            log.warn("读取对话记忆失败，返回空列表 | key={} | error={}", key, e.getMessage());
            return new ArrayList<>();
        }
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

    private void saveMessageRecords(String key, List<MessageRecord> records) {
        try {
            String json = objectMapper.writeValueAsString(records);
            redisTemplate.opsForValue().set(key, json, ttlDays, TimeUnit.DAYS);
        } catch (Exception e) {
            // Redis/序列化故障静默降级：写失败记 warn 不抛出，不阻断对话
            log.warn("保存对话记忆失败 | key={} | error={}", key, e.getMessage());
        }
    }

    private void triggerSummaryIfNeeded(String conversationId, int currentMessageCount) {
        if (!summaryEnabled || chatModel == null || currentMessageCount < summaryTriggerMessages) {
            return;
        }

        AtomicBoolean expired = new AtomicBoolean(false);
        CompletableFuture<Void> task = CompletableFuture.runAsync(
                () -> summarizeConversation(conversationId, expired),
                summaryExecutor
        ).orTimeout(summaryTimeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.debug("Conversation summary task failed | conversationId={} | error={}", conversationId, ex.getMessage());
                    return null;
                });

        // 超时后任务线程可能仍在运行：置位标志供写回前检查；remove(key, value) 只清理自己的登记，避免误删其他任务
        task.whenComplete((unused, throwable) -> {
            expired.set(true);
            summarizationTasks.remove(conversationId, task);
        });

        // putIfAbsent 保证登记的原子性，避免并发触发两个摘要任务互相覆盖
        if (summarizationTasks.putIfAbsent(conversationId, task) != null) {
            // 已有摘要任务在执行：取消本次，expired 置位后其写回逻辑会自动放弃
            task.cancel(false);
            return;
        }
        if (task.isDone()) {
            // 同步 executor 下任务可能已先行完成：清理登记，避免占位阻塞后续摘要
            summarizationTasks.remove(conversationId, task);
        }
    }

    private void summarizeConversation(String conversationId, AtomicBoolean expired) {
        String key = buildKey(conversationId);
        List<MessageRecord> snapshot = getMessageRecords(key);
        if (snapshot.size() <= summaryKeepMessages + 1) {
            return;
        }

        int splitIndex = Math.max(1, snapshot.size() - summaryKeepMessages);
        List<MessageRecord> toSummarize = new ArrayList<>(snapshot.subList(0, splitIndex));
        List<MessageRecord> toKeep = new ArrayList<>(snapshot.subList(splitIndex, snapshot.size()));

        String existingSummary = getSummaryContent(conversationId);
        String summaryInput = buildSummaryInput(existingSummary, toSummarize);
        if (summaryInput.isBlank()) {
            return;
        }

        String summary = generateSummary(summaryInput);
        if (summary == null || summary.isBlank()) {
            summary = truncate(summaryInput);
        }

        // orTimeout 只是放弃等待，任务线程仍会继续执行：写回前必须检查标志，超时任务不得写回
        if (expired.get()) {
            log.debug("Conversation summary expired before write-back | conversationId={}", conversationId);
            return;
        }

        // 写回前校验快照一致性，防止用旧快照整体覆盖摘要期间新增的消息：
        // - 列表未变：按快照尾部裁剪
        // - 仅追加了新消息（快照为前缀）：保留所有未参与摘要的消息
        // - 其他变更（并发裁剪/清空等）：放弃本轮摘要
        List<MessageRecord> current = getMessageRecords(key);
        final List<MessageRecord> kept;
        if (current.equals(snapshot)) {
            kept = toKeep;
        } else if (current.size() > snapshot.size() && current.subList(0, snapshot.size()).equals(snapshot)) {
            kept = new ArrayList<>(current.subList(toSummarize.size(), current.size()));
        } else {
            log.info("跳过本轮消息裁剪：对话消息已并发变更 | conversationId={}", conversationId);
            return;
        }

        try {
            redisTemplate.opsForValue().set(buildSummaryKey(conversationId), summary, ttlDays, TimeUnit.DAYS);
        } catch (Exception e) {
            // 摘要写失败则放弃裁剪，避免“摘要没存上、旧消息又被裁掉”的双重丢失
            log.warn("保存对话摘要失败，跳过本轮消息裁剪 | conversationId={} | error={}", conversationId, e.getMessage());
            return;
        }
        saveMessageRecords(key, kept);

        log.debug("Conversation summarized | conversationId={} | summarizedMessages={} | keptMessages={}",
                conversationId, toSummarize.size(), kept.size());
    }

    private String getSummaryContent(String conversationId) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(buildSummaryKey(conversationId));
        } catch (Exception e) {
            // Redis 故障静默降级：摘要读取失败按无摘要处理
            log.warn("读取对话摘要失败 | conversationId={} | error={}", conversationId, e.getMessage());
            return null;
        }
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
