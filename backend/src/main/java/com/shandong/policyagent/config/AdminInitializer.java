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
    /** 可选环境变量：配置后作为初始管理员密码；未配置时回退 admin/admin，保持本地开发与文档默认行为 */
    private static final String INITIAL_PASSWORD_ENV = "APP_ADMIN_INITIAL_PASSWORD";

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

        String initialPassword = System.getenv(INITIAL_PASSWORD_ENV);
        if (initialPassword == null || initialPassword.isBlank()) {
            initialPassword = ADMIN_PASSWORD;
            log.info("未配置 {}，使用默认初始密码（仅建议本地开发环境使用）", INITIAL_PASSWORD_ENV);
        } else {
            // 弱密码直接拒绝启动，避免生产环境带着弱口令上线
            validatePasswordStrength(initialPassword);
        }

        User admin = User.builder()
                .username(ADMIN_USERNAME)
                .password(passwordEncoder.encode(initialPassword))
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);
        // 密码（明文/密文）均不写入日志，防止凭据泄漏
        log.info("已创建默认管理员账号 {}，请尽快修改默认密码", ADMIN_USERNAME);
    }

    /**
     * 初始密码强度校验：少于 8 位或等于用户名视为弱密码，fail-fast 终止启动
     */
    private void validatePasswordStrength(String password) {
        if (password.length() < 8 || ADMIN_USERNAME.equals(password)) {
            log.error("{} 为弱密码（少于 8 位或等于用户名），拒绝启动", INITIAL_PASSWORD_ENV);
            throw new IllegalStateException(INITIAL_PASSWORD_ENV + " 为弱密码：请提供至少 8 位且不等于用户名的初始密码");
        }
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
