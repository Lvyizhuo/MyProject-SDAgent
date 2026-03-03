package com.shandong.policyagent.controller;

import com.shandong.policyagent.entity.Role;
import com.shandong.policyagent.entity.User;
import com.shandong.policyagent.model.AuthResponse;
import com.shandong.policyagent.model.admin.AdminChangePasswordRequest;
import com.shandong.policyagent.model.admin.AdminLoginRequest;
import com.shandong.policyagent.repository.UserRepository;
import com.shandong.policyagent.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminAuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * 管理员登录（验证角色为 ADMIN）
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.info("收到管理员登录请求: username={}", request.getUsername());

        // 认证用户名和密码
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // 获取用户并验证角色
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("该用户不是管理员，无法访问管理控制台");
        }

        log.info("管理员登录成功: {}", user.getUsername());

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }

    /**
     * 修改管理员密码
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody AdminChangePasswordRequest request,
            @AuthenticationPrincipal User currentUser) {

        log.info("收到修改密码请求: username={}", currentUser.getUsername());

        // 验证当前用户是管理员
        if (currentUser.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("只有管理员可以修改密码");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), currentUser.getPassword())) {
            throw new IllegalArgumentException("旧密码错误");
        }

        // 验证新密码一致性
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("新密码与确认密码不一致");
        }

        // 更新密码
        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);

        log.info("密码修改成功: username={}", currentUser.getUsername());
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }
}
