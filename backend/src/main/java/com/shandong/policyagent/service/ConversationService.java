package com.shandong.policyagent.service;

import com.shandong.policyagent.exception.BusinessException;
import com.shandong.policyagent.model.ChatMessage;
import com.shandong.policyagent.model.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String SESSION_KEY_PREFIX = "chat:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "user:sessions:";
    private static final long SESSION_TTL_DAYS = 30;

    public ChatSession createSession(Long userId, String sessionId) {
        ChatSession session = ChatSession.builder()
                .id(sessionId)
                .userId(userId)
                .title("新对话")
                .messages(new ArrayList<>())
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
        
        saveSession(session);
        addSessionToUserList(userId, sessionId);
        
        log.info("创建新会话: userId={}, sessionId={}", userId, sessionId);
        return session;
    }

    public ChatSession getSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        return (ChatSession) redisTemplate.opsForValue().get(key);
    }

    public ChatSession getOrCreateSession(Long userId, String sessionId) {
        ChatSession session = getSession(sessionId);
        if (session == null) {
            session = createSession(userId, sessionId);
            return session;
        }
        // IDOR 修复：会话已存在时校验归属（匿名用户只能访问 userId 为 null 的会话）
        assertSessionOwner(session, userId);
        return session;
    }

    public void saveSession(ChatSession session) {
        String key = SESSION_KEY_PREFIX + session.getId();
        session.setUpdatedAt(System.currentTimeMillis());
        redisTemplate.opsForValue().set(key, session, SESSION_TTL_DAYS, TimeUnit.DAYS);
    }

    // 残留风险：此处为 Redis 读-改-写，未做 Lua/分布式锁原子化，并发写同一会话时可能互相覆盖丢消息，
    // 与 ChatMemory 的摘要写回同属读写竞态问题，留待专项处理
    public void addMessage(String sessionId, ChatMessage message) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            if (session.getMessages() == null) {
                session.setMessages(new ArrayList<>());
            }
            session.getMessages().add(message);
            
            if (session.getMessages().size() == 1 && "user".equals(message.getRole())) {
                String title = message.getContent();
                if (title.length() > 30) {
                    title = title.substring(0, 30) + "...";
                }
                session.setTitle(title);
            }
            
            saveSession(session);
        }
    }

    public List<ChatSession> getUserSessions(Long userId) {
        String key = USER_SESSIONS_KEY_PREFIX + userId;
        Set<Object> sessionIds = redisTemplate.opsForSet().members(key);
        
        if (sessionIds == null || sessionIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<ChatSession> sessions = new ArrayList<>();
        for (Object sessionId : sessionIds) {
            ChatSession session = getSession(sessionId.toString());
            if (session != null) {
                sessions.add(session);
            }
        }
        
        sessions.sort((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return sessions;
    }

    public void deleteSession(Long userId, String sessionId) {
        // IDOR 修复：先校验归属再删除（会话已过期不存在时仍执行清理，保证幂等）
        assertSessionOwner(getSession(sessionId), userId);

        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;

        redisTemplate.delete(sessionKey);
        redisTemplate.opsForSet().remove(userSessionsKey, sessionId);

        log.info("删除会话: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 校验会话归属：不匹配抛 404（而非 403），避免通过状态码差异枚举探测他人会话 ID。
     * 匿名兼容：callerUserId 为 null 时仅允许访问 userId 同为 null 的会话。
     */
    private void assertSessionOwner(ChatSession session, Long callerUserId) {
        if (session != null && !Objects.equals(session.getUserId(), callerUserId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "会话不存在");
        }
    }

    private void addSessionToUserList(Long userId, String sessionId) {
        String key = USER_SESSIONS_KEY_PREFIX + userId;
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, SESSION_TTL_DAYS, TimeUnit.DAYS);
    }
}
