package com.shandong.policyagent.config;

import com.shandong.policyagent.entity.AgentConfig;
import com.shandong.policyagent.entity.Role;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.repository.AgentConfigRepository;
import com.shandong.policyagent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    private final UserRepository userRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final DefaultAgentConfigLoader defaultConfigLoader;

    @Override
    public void run(ApplicationArguments args) {
        initializeAdminUser();
        initializeAgentConfig();
    }

    /**
     * 初始化管理员账号（如果不存在）
     */
    private void initializeAdminUser() {
        if (userRepository.existsByUsername(ADMIN_USERNAME)) {
            log.info("管理员账号已存在: {}", ADMIN_USERNAME);
            return;
        }

        User admin = User.builder()
                .username(ADMIN_USERNAME)
                .password(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("管理员账号创建成功: {} (默认密码: {})", ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    /**
     * 初始化默认智能体配置（如果不存在）
     */
    private void initializeAgentConfig() {
        if (agentConfigRepository.findById(1L).isPresent()) {
            log.info("智能体配置已存在: id=1");
            return;
        }

        AgentConfig defaultConfig = AgentConfig.builder()
                .id(1L)
                .name(defaultConfigLoader.getDefaultName())
                .description(defaultConfigLoader.getDefaultDescription())
                .modelProvider("dashscope")
                .apiKey("${DASHSCOPE_API_KEY}")
                .apiUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .modelName("qwen3.5-plus")
                .temperature(0.70)
                .systemPrompt(defaultConfigLoader.getDefaultSystemPrompt())
                .greetingMessage(defaultConfigLoader.getDefaultGreetingMessage())
                .skills(defaultConfigLoader.getDefaultSkills())
                .mcpServersConfig(defaultConfigLoader.getDefaultMcpServersConfig())
                .build();

        agentConfigRepository.save(defaultConfig);
        log.info("默认智能体配置创建成功: id=1");
    }
}
