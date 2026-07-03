package com.shandong.policyagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shandong.policyagent.entity.AgentInstance;
import com.shandong.policyagent.entity.AgentModelBinding;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.exception.BusinessException;
import com.shandong.policyagent.model.dto.AgentCardDto;
import com.shandong.policyagent.model.dto.AgentUpsertRequest;
import com.shandong.policyagent.repository.AgentInstanceRepository;
import com.shandong.policyagent.repository.ModelProviderRepository;
import com.shandong.policyagent.repository.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInstanceService {

    private final AgentInstanceRepository agentRepository;
    private final ModelProviderRepository modelRepository;
    private final ToolDefinitionRepository toolRepository;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "chat", "对话型",
            "workflow", "工作流",
            "support", "客服型"
    );

    private static final Map<String, String> CATEGORY_TONES = Map.of(
            "chat", "secondary",
            "workflow", "warning",
            "support", "success"
    );

    @Transactional(readOnly = true)
    public List<AgentCardDto> listCards() {
        return agentRepository.findAllByOrderByIsDefaultDescIdAsc().stream()
                .map(this::toCardDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentCardDto getCard(Long id) {
        AgentInstance agent = agentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "智能体不存在"));
        return toCardDto(agent);
    }

    @Transactional
    public AgentCardDto create(AgentUpsertRequest request, User user) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            request.setCode(slugify(request.getName()));
        }
        if (agentRepository.findByCode(request.getCode()).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "编码已存在: " + request.getCode());
        }
        AgentInstance agent = newInstance(request, user);
        return toCardDto(agentRepository.save(agent));
    }

    @Transactional
    public AgentCardDto update(Long id, AgentUpsertRequest request, User user) {
        AgentInstance agent = agentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "智能体不存在"));
        if (request.getCode() != null && !request.getCode().equals(agent.getCode())) {
            agentRepository.findByCode(request.getCode()).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new BusinessException(HttpStatus.CONFLICT, "编码已存在: " + request.getCode());
                }
            });
            agent.setCode(request.getCode());
        }
        if (request.getName() != null) agent.setName(request.getName());
        if (request.getDescription() != null) agent.setDescription(request.getDescription());
        if (request.getCategory() != null) agent.setCategory(request.getCategory());
        if (request.getIcon() != null) agent.setIcon(request.getIcon());
        if (request.getStatus() != null) agent.setStatus(request.getStatus());
        if (request.getVersion() != null) agent.setVersion(request.getVersion());
        if (request.getKnowledgeFolderId() != null) agent.setKnowledgeFolderId(request.getKnowledgeFolderId());

        if (request.getBindings() != null) {
            agent.getBindings().clear();
            for (AgentUpsertRequest.BindingDto b : request.getBindings()) {
                agent.getBindings().add(buildBinding(agent, b));
            }
        }
        if (request.getEnabledTools() != null) {
            try {
                agent.setEnabledTools(objectMapper.writeValueAsString(request.getEnabledTools()));
            } catch (Exception e) {
                agent.setEnabledTools("[]");
            }
        }
        // 提示词相关字段
        if (request.getSystemPrompt() != null) agent.setSystemPrompt(request.getSystemPrompt());
        if (request.getGreetingMessage() != null) agent.setGreetingMessage(request.getGreetingMessage());
        if (request.getSkills() != null) {
            try {
                agent.setSkillsJson(objectMapper.writeValueAsString(request.getSkills()));
            } catch (Exception e) {
                agent.setSkillsJson("{}");
            }
        }
        if (request.getMcpServersConfig() != null) {
            try {
                agent.setMcpServersConfig(objectMapper.writeValueAsString(request.getMcpServersConfig()));
            } catch (Exception e) {
                agent.setMcpServersConfig("[]");
            }
        }
        return toCardDto(agentRepository.save(agent));
    }

    @Transactional
    public void delete(Long id) {
        AgentInstance agent = agentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "智能体不存在"));
        if (Boolean.TRUE.equals(agent.getIsDefault())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "默认智能体不可删除，请先切换默认");
        }
        agentRepository.delete(agent);
    }

    @Transactional
    public AgentCardDto setDefault(Long id) {
        // 先把其他所有智能体设为非默认
        agentRepository.findAll().forEach(a -> a.setIsDefault(false));
        agentRepository.flush();

        AgentInstance agent = agentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "智能体不存在"));
        agent.setIsDefault(true);
        return toCardDto(agentRepository.save(agent));
    }

    @Transactional
    public AgentCardDto duplicate(Long id) {
        AgentInstance source = agentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "智能体不存在"));
        String newCode = source.getCode() + "-copy-" + System.currentTimeMillis() % 1000;
        String newName = source.getName() + " (副本)";
        AgentInstance copy = AgentInstance.builder()
                .name(newName).code(newCode)
                .description(source.getDescription())
                .category(source.getCategory()).icon(source.getIcon())
                .status("draft").isDefault(false)
                .version("0.1").dailyCalls(0L)
                .enabledTools(source.getEnabledTools())
                .knowledgeFolderId(source.getKnowledgeFolderId())
                .systemPrompt(source.getSystemPrompt())
                .greetingMessage(source.getGreetingMessage())
                .skillsJson(source.getSkillsJson())
                .mcpServersConfig(source.getMcpServersConfig())
                .build();
        for (AgentModelBinding b : source.getBindings()) {
            copy.getBindings().add(AgentModelBinding.builder()
                    .agent(copy).role(b.getRole())
                    .modelId(b.getModelId()).toolCode(b.getToolCode())
                    .displayName(b.getDisplayName())
                    .build());
        }
        return toCardDto(agentRepository.save(copy));
    }

    // --- helpers ---

    private AgentInstance newInstance(AgentUpsertRequest request, User user) {
        AgentInstance agent = AgentInstance.builder()
                .name(request.getName()).code(request.getCode())
                .description(request.getDescription())
                .category(request.getCategory() != null ? request.getCategory() : "chat")
                .icon(request.getIcon() != null ? request.getIcon() : "MessageSquare")
                .status(request.getStatus() != null ? request.getStatus() : "draft")
                .version(request.getVersion() != null ? request.getVersion() : "0.1")
                .isDefault(false).dailyCalls(0L)
                .knowledgeFolderId(request.getKnowledgeFolderId())
                .createdBy(user != null ? user.getId() : null)
                .systemPrompt(request.getSystemPrompt() != null ? request.getSystemPrompt() : "")
                .greetingMessage(request.getGreetingMessage() != null ? request.getGreetingMessage() : "")
                .build();
        if (request.getBindings() != null) {
            for (AgentUpsertRequest.BindingDto b : request.getBindings()) {
                agent.getBindings().add(buildBinding(agent, b));
            }
        }
        if (request.getEnabledTools() != null) {
            try {
                agent.setEnabledTools(objectMapper.writeValueAsString(request.getEnabledTools()));
            } catch (Exception e) {
                agent.setEnabledTools("[]");
            }
        }
        if (request.getSkills() != null) {
            try {
                agent.setSkillsJson(objectMapper.writeValueAsString(request.getSkills()));
            } catch (Exception e) {
                agent.setSkillsJson("{}");
            }
        }
        if (request.getMcpServersConfig() != null) {
            try {
                agent.setMcpServersConfig(objectMapper.writeValueAsString(request.getMcpServersConfig()));
            } catch (Exception e) {
                agent.setMcpServersConfig("[]");
            }
        }
        return agent;
    }

    private AgentModelBinding buildBinding(AgentInstance agent, AgentUpsertRequest.BindingDto b) {
        String displayName = b.getDisplayName();
        if (displayName == null && b.getModelId() != null) {
            displayName = modelRepository.findById(b.getModelId())
                    .map(p -> p.getName() + " · " + p.getModelName())
                    .orElse(null);
        }
        if (displayName == null && b.getToolCode() != null) {
            displayName = toolRepository.findByCode(b.getToolCode())
                    .map(t -> t.getName())
                    .orElse(b.getToolCode());
        }
        return AgentModelBinding.builder()
                .agent(agent).role(b.getRole())
                .modelId(b.getModelId()).toolCode(b.getToolCode())
                .displayName(displayName)
                .build();
    }

    private static final Map<String, String> ROLE_LABELS = Map.of(
            "LLM", "大语言模型",
            "VISION", "视觉",
            "AUDIO", "语音",
            "EMBEDDING", "嵌入",
            "RERANK", "重排序",
            "FUNCTION", "函数工具"
    );

    private AgentCardDto toCardDto(AgentInstance agent) {
        List<AgentCardDto.BindingDto> bindings = agent.getBindings().stream()
                .map(b -> AgentCardDto.BindingDto.builder()
                        .role(b.getRole())
                        .roleLabel(Optional.ofNullable(ROLE_LABELS.get(b.getRole())).orElse(b.getRole()))
                        .modelId(b.getModelId())
                        .toolCode(b.getToolCode())
                        .modelName(b.getDisplayName() != null ? b.getDisplayName() : fallbackName(b))
                        .build())
                .collect(Collectors.toList());

        // 反序列化 enabledTools / skills / mcpServersConfig
        List<String> enabledTools = parseStringList(agent.getEnabledTools());
        Map<String, Object> skills = parseMap(agent.getSkillsJson());
        List<Map<String, Object>> mcpServersConfig = parseMapList(agent.getMcpServersConfig());

        return AgentCardDto.builder()
                .id(agent.getId())
                .name(agent.getName())
                .code(agent.getCode())
                .description(agent.getDescription())
                .category(agent.getCategory())
                .categoryLabel(Optional.ofNullable(CATEGORY_LABELS.get(agent.getCategory())).orElse(""))
                .categoryTone(Optional.ofNullable(CATEGORY_TONES.get(agent.getCategory())).orElse("muted"))
                .icon(agent.getIcon())
                .status(agent.getStatus())
                .isDefault(agent.getIsDefault())
                .version(agent.getVersion())
                .dailyCalls(agent.getDailyCalls() != null ? agent.getDailyCalls() : 0L)
                .updatedAtLabel(formatRelative(agent.getUpdatedAt()))
                .knowledgeFolderId(agent.getKnowledgeFolderId())
                .bindings(bindings)
                .enabledTools(enabledTools)
                .systemPrompt(agent.getSystemPrompt())
                .greetingMessage(agent.getGreetingMessage())
                .skills(skills)
                .mcpServersConfig(mcpServersConfig)
                .build();
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> parseMapList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String fallbackName(AgentModelBinding b) {
        if (b.getModelId() != null) {
            return modelRepository.findById(b.getModelId())
                    .map(p -> p.getModelName())
                    .orElse("Model#" + b.getModelId());
        }
        if (b.getToolCode() != null) {
            return b.getToolCode();
        }
        return "未配置";
    }

    private String formatRelative(java.time.LocalDateTime time) {
        if (time == null) return "未知";
        java.time.Duration d = java.time.Duration.between(time, java.time.LocalDateTime.now());
        if (d.toMinutes() < 1) return "刚刚";
        if (d.toMinutes() < 60) return d.toMinutes() + " 分钟前";
        if (d.toHours() < 24) return d.toHours() + " 小时前";
        if (d.toDays() < 30) return d.toDays() + " 天前";
        return time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String slugify(String name) {
        if (name == null) return "agent";
        String s = name.toLowerCase()
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9\\-一-龥]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (s.isEmpty()) s = "agent";
        // 简单的去重：若已存在则加后缀
        String base = s;
        int n = 1;
        while (agentRepository.findByCode(s).isPresent()) {
            s = base + "-" + (++n);
        }
        return s;
    }
}
