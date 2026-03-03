package com.shandong.policyagent.controller;

import com.shandong.policyagent.config.DynamicAgentConfigHolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开配置接口
 *
 * 提供不需要认证的配置接口，用于前端获取智能体的公开配置信息（如开场白）。
 */
@Slf4j
@RestController
@RequestMapping("/api/public/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PublicConfigController {

    private final DynamicAgentConfigHolder dynamicAgentConfigHolder;

    /**
     * 获取智能体公开配置
     *
     * GET /api/public/config/agent
     *
     * @return 公开配置响应（包含开场白等）
     */
    @GetMapping("/agent")
    public ResponseEntity<PublicAgentConfigResponse> getAgentConfig() {
        String greetingMessage = dynamicAgentConfigHolder.get() != null
                ? dynamicAgentConfigHolder.get().getGreetingMessage()
                : null;

        return ResponseEntity.ok(PublicAgentConfigResponse.builder()
                .greetingMessage(greetingMessage)
                .build());
    }

    /**
     * 公开智能体配置响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicAgentConfigResponse {
        /**
         * 开场白
         */
        private String greetingMessage;
    }
}
