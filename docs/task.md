# 管理员控制台开发任务清单

## 项目概述

基于 PRD v1.1 文档，实现管理员控制台功能，允许管理员在运行时动态配置智能体参数（AI模型、系统提示词、技能模块、开场白等），配置修改后实时生效。

**技术栈**：
- 后端：Spring Boot 3.4 + Spring AI 1.0.3 + Spring Security + JWT
- 前端：React 19 + Vite 7
- 数据库：PostgreSQL 16 + pgvector
- 缓存：Redis 7

---

## 📋 阶段 1：后端基础（预计 3-4 天）

### 任务 1.1：创建数据库表和实体

**优先级**：P0（阻塞）
**状态**：✅ 已完成
**负责人**：Claude

#### 子任务

- [x] **1.1.1** 创建 `agent_config` 表
  - 文件：`backend/src/main/main/resources/db/migration/V2__create_agent_config.sql`
  - 参考 PRD 第 5.1 节的 SQL 定义
  - 包含字段：name, description, model_provider, api_key, api_url, model_name, temperature, system_prompt, greeting_message, skills, mcp_servers_config
  - 添加单配置约束 `CONSTRAINT single_agent CHECK (id = 1)`
  - 添加默认配置数据

- [x] **1.1.2** 创建 `AgentConfig` 实体类
  - 文件：`backend/src/main/java/com/shandong/policyagent/entity/AgentConfig.java`
  - 使用 JPA 注解：`@Entity`, `@Table`, `@Lob`, `@JdbcTypeCode(SqlTypes.JSON)`
  - 使用 Lombok：`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
  - 添加 `@PrePersist` 和 `@PreUpdate` 自动时间戳
  - 创建内嵌类 `McpServerConfig` 用于 MCP 配置

- [x] **1.1.3** 创建 `AgentConfigRepository`
  - 文件：`backend/src/main/java/com/shandong/policyagent/repository/AgentConfigRepository.java`
  - 继承 `JpaRepository<AgentConfig, Long>`
  - 添加方法：`Optional<AgentConfig> findFirstByOrderByIdAsc()`

**验收标准**：
- ✅ Flyway 迁移脚本成功执行
- ✅ 实体类能正确映射到数据库表
- ✅ JSONB 字段能正确序列化（skills, mcp_servers_config）

---

### 任务 1.2：实现管理员初始化

**优先级**：P0（阻塞）
**状态**：✅ 已完成
**负责人**：Claude

#### 子任务

- [x] **1.2.1** 创建 `AdminInitializer` 组件
  - 文件：`backend/src/main/java/com/shandong/policyagent/config/AdminInitializer.java`
  - 实现 `ApplicationRunner` 接口，在应用启动时执行
  - 检测 `users` 表是否存在 admin 账号，不存在则创建
  - 默认账号：username=`admin`, password=`admin`（BCrypt 加密）, role=`ADMIN`
  - 检测 `agent_config` 表是否有记录，不存在则创建默认配置

- [x] **1.2.2** 创建默认配置加载器
  - 文件：`backend/src/main/java/com/shandong/policyagent/config/DefaultAgentConfigLoader.java`
  - 提供默认配置模板（系统提示词、开场白、技能配置）
  - 参考 PRD 第 3.3 节的默认值

**验收标准**：
- ✅ 应用启动时自动创建 admin 账号（仅首次）
- ✅ 应用启动时自动创建默认 agent_config（仅首次）
- ✅ 不影响现有数据和用户

---

### 任务 1.3：创建管理员认证 API

**优先级**：P0（阻塞）
**状态**：✅ 已完成
**负责人**：Claude

#### 子任务

- [x] **1.3.1** 创建 `AdminAuthController`
  - 文件：`backend/src/main/java/com/shandong/policyagent/controller/AdminAuthController.java`
  - 路径：`/api/admin/auth`
  - 端点：
    - `POST /login` - 管理员登录（验证角色为 ADMIN）
    - `POST /change-password` - 修改密码（验证旧密码）
  - 使用现有的 `JwtService` 和 `AuthService`
  - 添加 ADMIN 角色验证

- [x] **1.3.2** 创建 DTO 类
  - 文件：`backend/src/main/java/com/shandong/policyagent/model/admin/AdminLoginRequest.java`
  - 文件：`backend/src/main/java/com/shandong/policyagent/model/admin/AdminChangePasswordRequest.java`
  - 添加 JSR-303 验证注解（`@NotBlank`, `@Size`）

- [x] **1.3.3** 更新 SecurityConfig
  - 文件：`backend/src/main/java/com/shandong/policyagent/config/SecurityConfig.java`
  - 确认 `/api/admin/**` 路径需要 ADMIN 角色
  - 确认 `/api/admin/auth/login` 允许匿名访问

**验收标准**：
- ✅ 管理员可以登录并获得 JWT Token
- ✅ 普通用户无法访问管理员接口（403 Forbidden）
- ✅ 修改密码功能正常，旧密码验证有效

---

### 任务 1.4：实现智能体配置 CRUD API

**优先级**：P0（阻塞）
**状态**：✅ 已完成
**负责人**：Claude

#### 子任务

- [x] **1.4.1** 创建 `AgentConfigController`
  - 文件：`backend/src/main/java/com/shandong/policyagent/controller/AgentConfigController.java`
  - 路径：`/api/admin/agent-config`
  - 端点：
    - `GET /` - 获取当前配置（API Key 脱敏）
    - `PUT /` - 更新配置（调用同步服务）
    - `POST /reset` - 重置为默认配置
  - 添加 `@PreAuthorize("hasRole('ADMIN')")` 权限注解

- [x] **1.4.2** 创建 `AgentConfigService`
  - 文件：`backend/src/main/java/com/shandong/policyagent/service/AgentConfigService.java`
  - 实现配置 CRUD 逻辑
  - 实现 API Key 脱敏方法（前 4 位 + 后 4 位）
  - 实现配置验证（提示词非空、模型名有效）

- [x] **1.4.3** 创建 DTO 类
  - 文件：`backend/src/main/java/com/shandong/policyagent/model/admin/AgentConfigRequest.java`
  - 文件：`backend/src/main/java/com/shandong/policyagent/model/admin/AgentConfigResponse.java`
  - 使用 `List<Map<String, Object>>` 表示 mcp_servers_config（已修复 Map 类型不匹配 Bug）

**验收标准**：
- ✅ 可以获取当前配置，API Key 已脱敏
- ✅ 可以更新配置，数据持久化到数据库
- ✅ 配置验证有效，无效配置返回 400 错误
- ✅ 重置功能恢复到默认值

---

## 📋 阶段 2：配置同步机制（预计 2-3 天）

### 任务 2.1：实现配置同步服务

**优先级**：P0（阻塞）
**状态**：✅ 已完成
**负责人**：Claude

#### 子任务

- [x] **2.1.1** 创建 `AgentConfigSyncService`
  - 文件：`backend/src/main/java/com/shandong/policyagent/service/AgentConfigSyncService.java`
  - 方法：`syncConfigToRuntime(AgentConfig config)` + `reloadFromDatabase()`
  - 通过 `DynamicAgentConfigHolder.update()` 将最新配置同步到运行时内存

- [x] **2.1.2** 创建 `DynamicAgentConfigHolder`
  - 文件：`backend/src/main/java/com/shandong/policyagent/config/DynamicAgentConfigHolder.java`
  - `AtomicReference<AgentConfig>` 持有当前配置，`@PostConstruct` 从 DB 加载
  - 提供 `getSystemPrompt()` / `getModelName()` / `getTemperature()` / `getSkills()`

- [x] **2.1.3** 修改 `ChatClientConfig` + `ChatService` 实现 per-request 动态注入
  - 移除硬编码 `defaultSystem(SYSTEM_PROMPT)`
  - `ChatService` 每次 prompt 调用 `.system(holder.getSystemPrompt())` + `.options(buildChatOptions())`

- [x] **2.1.4** 创建 `ToolStateManager` + 各工具加 enabled 检查
  - 文件：`backend/src/main/java/com/shandong/policyagent/tool/ToolStateManager.java`
  - `WebSearchTool`、`SubsidyCalculatorTool`、`FileParserTool` 均在 lambda 开头调用 `isXxxEnabled()`

- [x] **2.1.5** `AgentConfigController` 接入同步服务
  - 注入 `AgentConfigSyncService`，在 `updateConfig` 和 `resetConfig` 后调用 `reloadFromDatabase()`

**验收标准**：
- ✅ 配置更新后立即同步到运行时（无需重启）
- ✅ System Prompt 修改后，后续对话使用新提示词
- ✅ Model 参数修改后，后续对话使用新参数
- ✅ 工具启用/禁用通过 ToolStateManager 实时生效

---

### 任务 2.2：实现配置测试 API

**优先级**：P1（功能）
**状态**：✅ 已完成
**负责人**：Claude

#### 子任务

- [x] **2.2.1** 创建 `AgentConfigTestController`
  - 文件：`backend/src/main/java/com/shandong/policyagent/controller/AgentConfigTestController.java`
  - 路径：`POST /api/admin/agent-config/test`
  - 接受测试消息，通过 `ChatService.chat()` 发起对话，返回 AI 回复

- [x] **2.2.2** 创建测试 DTO
  - 文件：`backend/src/main/java/com/shandong/policyagent/model/admin/AgentConfigTestRequest.java`
  - 字段：`message`（必填）、`sessionId`（可选，不填则生成 `admin-test-{UUID}`）

**验收标准**：
- ✅ 可以使用当前配置发起测试对话
- ✅ 未提供 sessionId 时自动生成隔离的测试会话
- ✅ 测试接口受 ADMIN 角色保护

---

## 📋 阶段 3：前端开发（预计 4-5 天）

### 任务 3.1：创建管理员 API 服务

**优先级**：P0（阻塞）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **3.1.1** 创建 `adminApi.js`
  - 文件：`frontend/src/services/adminApi.js`
  - 导出方法：
    - `adminLogin(username, password)`
    - `adminChangePassword(oldPassword, newPassword, confirmPassword)`
    - `getAgentConfig()`
    - `updateAgentConfig(config)`
    - `resetAgentConfig()`
    - `testAgentConfig(message, sessionId)`
  - 自动注入管理员 JWT Token
  - 参考现有的 `api.js` 模式

**验收标准**：
- ✅ 所有 API 方法都能正确调用后端
- ✅ 错误处理统一（401、403、404、500）
- ✅ Loading 状态管理

---

### 任务 3.2：扩展认证 Context

**优先级**：P0（阻塞）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **3.2.1** 扩展 `AuthContext`
  - 文件：`frontend/src/context/AuthContext.jsx`
  - 添加管理员状态：`isAdmin`（基于 `user.role === 'ADMIN'`）
  - 添加管理员登录方法：`adminLogin(username, password)`
  - 添加管理员密码修改方法：`adminChangePassword(...)`

- [ ] **3.2.2** 更新 TopNavbar
  - 文件：`frontend/src/components/TopNavbar.jsx`
  - 在用户下拉菜单中添加"管理员控制台"入口（仅当 `isAdmin` 为 true 时显示）

**验收标准**：
- ✅ 管理员可以看到"管理员控制台"入口
- ✅ 普通用户看不到管理员入口
- ✅ 点击管理员入口导航到 `/admin-console` 路由

---

### 任务 3.3：创建管理员控制台主页面

**优先级**：P0（阻塞）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **3.3.1** 创建 `AdminConsolePage.jsx`
  - 文件：`frontend/src/pages/AdminConsolePage.jsx`
  - 布局：左侧配置面板（420px）+ 右侧预览面板（剩余空间）
  - 参考现有页面布局模式（如 `ChatPage.jsx` 的 Sidebar + ChatWindow）
  - 状态管理：使用 `useState` 管理配置表单状态
  - 加载逻辑：页面加载时调用 `getAgentConfig()` 初始化表单

- [ ] **3.3.2** 创建 `AdminConsolePage.css`
  - 文件：`frontend/src/pages/AdminConsolePage.css`
  - 复用 `variables.css` 中的设计变量
  - 实现玻璃拟态风格（`backdrop-filter: blur()`）
  - 实现响应式布局（移动端支持）

- [ ] **3.3.3** 添加路由
  - 文件：`frontend/src/App.jsx`
  - 添加路由：`<Route path="/admin-console" element={<ProtectedRoute><AdminConsolePage /></ProtectedRoute>} />`

**验收标准**：
- ✅ 页面可以正常访问（仅管理员）
- ✅ 左右面板布局正确
- ✅ 样式与现有页面风格一致

---

### 任务 3.4：创建配置面板组件

**优先级**：P0（阻塞）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **3.4.1** 创建 `ConfigPanel.jsx`
  - 文件：`frontend/src/components/admin/ConfigPanel.jsx`
  - 组件结构：
    - 智能体基础信息（名称、描述）
    - AI 模型配置（提供商、API Key、API URL、模型名称、温度）
    - 系统提示词（多行文本域）
    - 技能模块配置（webSearch、subsidyCalculator、fileParser）
    - 开场白（多行文本域）
    - 操作按钮（保存、重置）
  - 实现表单验证（提示词非空、温度范围 0.0-1.0）

- [ ] **3.4.2** 创建 `ConfigPanel.css`
  - 文件：`frontend/src/components/admin/ConfigPanel.css`
  - 输入框样式：边框、圆角、背景色
  - 按钮样式：主按钮（渐变）、次按钮
  - 表单分组：使用折叠面板或分组标题

- [ ] **3.4.3** 实现保存和重置逻辑
  - 保存按钮：调用 `updateAgentConfig()`，成功后显示提示
  - 重置按钮：恢复到上次保存的状态（使用独立 state）
  - 确认提示：重置操作前显示确认对话框

**验收标准**：
- ✅ 所有配置字段都可以编辑
- ✅ 保存功能正常，数据提交到后端
- ✅ 重置功能正常，恢复到保存状态
- ✅ 表单验证有效

---

### 任务 3.5：创建预览面板组件

**优先级**：P1（功能）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **3.5.1** 创建 `PreviewPanel.jsx`
  - 文件：`frontend/src/components/admin/PreviewPanel.jsx`
  - 标签页导航：聊天测试 | 配置 JSON | 效果预览
  - 通过 props 接收当前配置状态
  - 实时更新：配置面板修改时，预览区自动更新

- [ ] **3.5.2** 创建 `ChatTestTab.jsx`
  - 文件：`frontend/src/components/admin/ChatTestTab.jsx`
  - 复用现有的 `ChatWindow` 和 `MessageBubble` 组件
  - 调用 `testAgentConfig()` API
  - 独立会话管理（不影响主系统）

- [ ] **3.5.3** 创建 `ConfigJsonTab.jsx`
  - 文件：`frontend/src/components/admin/ConfigJsonTab.jsx`
  - 以格式化 JSON 展示当前配置
  - 支持复制到剪贴板功能

- [ ] **3.5.4** 创建 `EffectPreviewTab.jsx`
  - 文件：`frontend/src/components/admin/EffectPreviewTab.jsx`
  - 展示配置摘要（名称、模型、提示词长度、启用的技能等）
  - 预览开场白的显示效果

**验收标准**：
- ✅ 可以在标签页之间切换
- ✅ 聊天测试功能正常
- ✅ 配置 JSON 正确显示和格式化
- ✅ 效果预览实时更新

---

### 任务 3.6：实现管理员登录页面

**优先级**：P1（功能）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **3.6.1** 修改 `LoginPage.jsx`
  - 文件：`frontend/src/pages/LoginPage.jsx`
  - 添加"管理员登录"选项卡
  - 或创建独立的 `AdminLoginPage.jsx`
  - 调用 `adminApi.adminLogin()` 而非普通登录

- [ ] **3.6.2** 添加密码修改对话框
  - 在用户中心或管理员控制台添加修改密码入口
  - 显示对话框：旧密码、新密码、确认密码
  - 调用 `adminApi.adminChangePassword()`

**验收标准**：
- ✅ 管理员可以登录
- ✅ 登录后获得管理员权限标识
- ✅ 修改密码功能正常

---

## 📋 阶段 4：测试与优化（预计 2-3 天）

### 任务 4.1：后端单元测试

**优先级**：P2（质量）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **4.1.1** `AdminAuthControllerTest`
  - 测试管理员登录（成功/失败）
  - 测试修改密码（成功/旧密码错误/新密码不一致）

- [ ] **4.1.2** `AgentConfigControllerTest`
  - 测试获取配置
  - 测试更新配置（成功/验证失败）
  - 测试重置配置

- [ ] **4.1.3** `AgentConfigServiceTest`
  - 测试配置 CRUD 逻辑
  - 测试 API Key 脱敏
- [ ] **4.1.4** `AdminInitializerTest`
  - 测试管理员初始化逻辑

---

### 任务 4.2：后端集成测试

**优先级**：P2（质量）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **4.2.1** 配置更新流程测试
  - 前端提交 → 数据库持久化 → 运行时同步 → 验证新配置生效

- [ ] **4.2.2** 权限控制测试
  - 非管理员访问被拒绝（403）
  - 未登录访问被拒绝（401）

---

### 任务 4.3：前端测试

**优先级**：P2（质量）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **4.3.1** 组件测试
  - `ConfigPanel` 组件渲染和交互
  - `PreviewPanel` 组件标签页切换
  - `ChatTestTab` 聊天测试功能

- [ ] **4.3.2** API 测试
  - `adminApi` 各方法的正确调用
  - 错误处理逻辑

---

### 任务 4.4：手动测试

**优先级**：P1（验证）
**状态**：⏳ 待开始
**负责人**：待分配

#### 测试场景

| 场景 | 预期结果 | 状态 |
|------|----------|------|
| 使用 admin/admin 登录 | 登录成功，获得 Token | ⏳ |
| 修改系统提示词 | 智能体行为符合新提示词 | ⏳ |
| 修改模型名称为 qwen3-max | 后续对话使用 qwen3-max | ⏳ |
| 修改 temperature 为 0.9 | 后续对话更随机 | ⏳ |
| 禁用 webSearch 技能 | 联网搜索不再被调用 | ⏳ |
| 修改开场白 | 前端聊天页面显示新开场白 | ⏳ |
| 使用聊天测试功能 | 可以发起测试对话，结果正确 | ⏳ |
| 普通用户访问管理控制台 | 403 Forbidden 或重定向 | ⏳ |

---

### 任务 4.5：Bug 修复和优化

**优先级**：P1（质量）
**状态**：⏳ 待开始
**负责人**：待分配

#### 子任务

- [ ] **4.5.1** 修复测试中发现的问题
- [ ] **4.5.2** 性能优化
  - 配置同步延迟 < 1s
  - 预览区实时更新延迟 < 100ms
- [ ] **4.5.3** 用户体验优化
  - 添加 Loading 状态提示
  - 优化错误消息提示
  - 添加操作成功提示

---

## 📊 总体进度

| 阶段 | 进度 | 状态 |
|------|------|------|
| 阶段 1：后端基础 | 100% | ✅ 已完成 |
| 阶段 2：配置同步机制 | 100% | ✅ 已完成 |
| 阶段 3：前端开发 | 0% | ⏳ 待开始 |
| 阶段 4：测试与优化 | 0% | ⏳ 待开始 |
| **总体进度** | **50%** | 🔄 进行中 |

---

## 📁 关键文件路径

### 后端文件

| 文件 | 说明 |
|------|------|
| `backend/src/main/resources/db/migration/V2__create_agent_config.sql` | 数据库迁移脚本 |
| `backend/src/main/java/com/shandong/policyagent/entity/AgentConfig.java` | 智能体配置实体 |
| `backend/src/main/java/com/shandong/policyagent/repository/AgentConfigRepository.java` | 配置 Repository |
| `backend/src/main/java/com/shandong/policyagent/config/AdminInitializer.java` | 管理员初始化器 |
| `backend/src/main/java/com/shandong/policyagent/config/DefaultAgentConfigLoader.java` | 默认配置加载器 |
| `backend/src/main/java/com/shandong/policyagent/controller/AdminAuthController.java` | 管理员认证 API |
| `backend/src/main/java/com/shandong/policyagent/controller/AgentConfigController.java` | 智能体配置 API |
| `backend/src/main/java/com/shandong/policyagent/controller/AgentConfigTestController.java` | 配置测试 API |
| `backend/src/main/java/com/shandong/policyagent/service/AgentConfigService.java` | 配置 CRUD 服务 |
| `backend/src/main/java/com/shandong/policyagent/service/AgentConfigSyncService.java` | 配置同步服务 |
| `backend/src/main/java/com/shandong/policyagent/config/DynamicChatClientConfig.java` | 动态 ChatClient 配置 |
| `backend/src/main/java/com/shandong/policyagent/tool/ToolStateManager.java` | 工具状态管理器 |

### 前端文件

| 文件 | 说明 |
|------|------|
| `frontend/src/services/adminApi.js` | 管理员 API 服务 |
| `frontend/src/context/AuthContext.jsx` | 认证 Context（扩展） |
| `frontend/src/components/TopNavbar.jsx` | 顶部导航栏（扩展） |
| `frontend/src/pages/AdminConsolePage.jsx` | 管理员控制台主页面 |
| `frontend/src/pages/AdminConsolePage.css` | 管理员控制台样式 |
| `frontend/src/components/admin/ConfigPanel.jsx` | 配置面板组件 |
| `frontend/src/components/admin/ConfigPanel.css` | 配置面板样式 |
| `frontend/src/components/admin/PreviewPanel.jsx` | 预览面板组件 |
| `frontend/src/components/admin/ChatTestTab.jsx` | 聊天测试标签页 |
| `frontend/src/components/admin/ConfigJsonTab.jsx` | 配置 JSON 标签页 |
| `frontend/src/components/admin/EffectPreviewTab.jsx` | 效果预览标签页 |

---

## 🔗 参考文档

- PRD 文档：`/docs/admin-console-prd.md`
- CLAUDE.md 项目说明：`/CLAUDE.md`
- 后端架构参考：
  - `backend/src/main/java/com/shandong/policyagent/config/ChatClientConfig.java`（现有 ChatClient 配置）
  - `backend/src/main/java/com/shandong/policyagent/service/AuthService.java`（现有认证服务）
- 前端架构参考：
  - `frontend/src/services/api.js`（现有 API 模式）
  - `frontend/src/context/AuthContext.jsx`（现有认证模式）

---

## ⚠️ 技术难点和注意事项

### 难点 1：ChatClient 动态配置

**问题**：Spring AI 的 ChatClient 默认是单例 Bean，System Prompt 和 Model 配置在创建后不可修改。

**解决方案选项**：
1. **方案 A（推荐）**：使用 `@Scope("prototype")` + `ObjectProvider<ChatClient>` 每次请求创建新实例，从数据库读取配置
2. **方案 B**：使用 `@RefreshScope` + Cloud Config 实现配置热刷新
3. **方案 C**：自定义 ChatClient 包装器，内部维护可变配置

**建议**：优先尝试方案 A，符合 Spring AI 最佳实践。

---

### 难点 2：工具启用/禁用动态管理

**问题**：工具通过 `@Bean` 注解注册，运行时难以启用/禁用。

**解决方案选项**：
1. **方案 A（推荐）**：在工具执行前检查 skills 配置，不满足条件则跳过
2. **方案 B**：使用 `@ConditionalOnProperty` 但需要重启
3. **方案 C**：创建工具包装器，内部调用真实工具前检查配置

**建议**：优先尝试方案 C，在 `ToolStateManager` 中实现统一的启用检查。

---

### 注意事项

1. **安全性**：所有管理员 API 必须验证 JWT Token 和 ADMIN 角色
2. **API Key 脱敏**：返回配置时必须脱敏 API Key
3. **配置验证**：提交前验证配置完整性（提示词非空、模型名有效等）
4. **环境变量处理**：支持 API Key 使用环境变量引用（如 `${DASHSCOPE_API_KEY}`）
5. **回滚机制**：配置同步失败时保留上次有效配置，避免系统崩溃
6. **样式一致性**：前端样式必须与现有页面风格完全一致

---

## 📝 更新日志

| 日期 | 更新内容 |
|------|----------|
| 2026-03-03 | 初始版本创建，基于 PRD v1.1 文档 |
| 2026-03-03 | 阶段2完成：DynamicAgentConfigHolder、AgentConfigSyncService、ToolStateManager、各工具 enabled 检查、AgentConfigTestController |

---

**文档版本**：v1.0
**创建日期**：2026-03-03
**状态**：✅ 已就绪
