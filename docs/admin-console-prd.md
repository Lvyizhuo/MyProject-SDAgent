# 管理员控制台 PRD

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档名称 | 管理员控制台 - 智能体配置系统 |
| 创建日期 | 2026-03-02 |
| 版本 | v1.1 |
| 状态 | 已评审 |

---

## 1. 项目概述

### 1.1 背景

山东省智能政策咨询助手当前使用硬编码的配置，智能体参数（如 AI 模型、系统提示词、技能模块等）无法在运行时动态调整。为提升系统的灵活性和可维护性，需要开发管理员控制台功能。

### 1.2 目标

- 提供可视化的智能体配置界面，允许管理员在运行时修改智能体参数
- 配置修改后实时生效，无需重启应用
- 保持与现有前端界面风格完全一致
- 确保管理员操作的安全性和权限控制

### 1.3 范围

- 管理员账号管理（初始化、登录、修改密码）
- 智能体配置管理（单智能体模式）
  - 智能体名称
  - 描述
  - AI 模型选择
  - 系统提示词设定
  - 技能模块配置（工具调用、MCP 配置、联网搜索等）
  - 开场白设定
- 实时预览功能
- 配置同步到运行时

---

## 2. 用户角色

### 2.1 管理员

| 角色 | 职责 | 权限 |
|------|------|------|
| 管理员 | 配置和管理智能体参数 | 访问管理员控制台，修改配置，测试配置 |

### 2.2 普通用户

| 角色 | 职责 | 权限 |
|------|------|------|
| 普通用户 | 使用智能体进行政策咨询 | 使用智能体，无配置权限 |

---

## 3. 功能需求

### 3.1 管理员认证

#### FR-1: 管理员初始化

| 需求描述 | 详细说明 |
|----------|----------|
| 初始账号 | 用户名：`admin`，密码：`admin` |
| 初始化时机 | 应用启动时自动检测，若不存在则创建 |
| 数据存储 | 存储在 `users` 表，role 为 `ADMIN` |

#### FR-2: 管理员登录

| 需求描述 | 详细说明 |
|----------|----------|
| 登录接口 | `POST /api/admin/auth/login` |
| 验证逻辑 | 验证用户名和密码，检查角色为 ADMIN |
| 返回结果 | JWT Token，后续请求需携带 |

#### FR-3: 修改密码

| 需求描述 | 详细说明 |
|----------|----------|
| 接口 | `POST /api/admin/auth/change-password` |
| 参数 | 旧密码、新密码、确认密码 |
| 验证 | 旧密码验证、新密码长度检查（至少 6 位） |

---

### 3.2 智能体配置管理

#### FR-4: 查看当前配置

| 需求描述 | 详细说明 |
|----------|----------|
| 接口 | `GET /api/admin/agent-config` |
| 返回内容 | 智能体名称、描述、模型配置、系统提示词、技能配置等 |
| 数据源 | `agent_config` 表 |

#### FR-5: 更新配置

| 需求描述 | 详细说明 |
|----------|----------|
| 接口 | `PUT /api/admin/agent-config` |
| 修改字段 | 智能体名称、描述、模型名称、系统提示词、技能配置、开场白 |
| 同步机制 | 更新后立即同步到运行时，无需重启 |
| 验证 | 配置完整性验证（提示词非空、模型名有效等） |

#### FR-6: 重置配置

| 需求描述 | 详细说明 |
|----------|----------|
| 接口 | `POST /api/admin/agent-config/reset` |
| 行为 | 恢复到默认配置 |
| 确认 | 操作前需要确认提示 |

---

### 3.3 配置字段定义

#### FR-7: 智能体基础信息

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| name | String | "政策问答智能体" | 智能体名称，最大 100 字符 |
| description | String | "用于山东省以旧换新补贴政策咨询" | 智能体描述，用于前端展示 |

#### FR-8: AI 模型配置

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| model_provider | String | "dashscope" | 模型提供商 |
| api_key | String | "${DASHSCOPE_API_KEY}" | API 密钥（支持环境变量引用，存储在本地环境变量） |
| api_url | String | "https://dashscope.aliyuncs.com/compatible-mode" | API 基础 URL |
| model_name | String | "qwen3.5-plus" | 模型名称 |
| temperature | Decimal | 0.70 | 模型温度参数（0.0-1.0） |

#### FR-9: 系统提示词

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| system_prompt | Text | （见下方） | 智能体的系统提示词 |

**默认系统提示词**：
```
你是一个专业的山东省以旧换新补贴政策咨询助手。你的任务是：

1. 准确理解用户关于补贴政策的咨询问题
2. 基于提供的政策文档内容回答问题
3. 当信息不足时，可以主动询问用户更多细节
4. 对于补贴金额计算，使用提供的工具进行精确计算
5. 保持回答准确、客观、易于理解

请始终基于提供的事实依据回答，不要编造信息。
```

#### FR-10: 开场白

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| greeting_message | Text | （见下方） | 智能体的开场问候语 |

**默认开场白**：
```
您好！我是山东省以旧换新政策咨询智能助手。您可以问我关于汽车、家电、数码产品等的补贴标准和申请流程。

**我可以帮您：**
- 查询各类产品补贴金额
- 了解申请条件和流程
- 计算您能获得的补贴
- 解答政策相关疑问
```

#### FR-11: 技能模块配置

| 字段 | 类型 | 说明 |
|------|------|------|
| skills | JSONB | 技能模块配置，包含以下子项 |

**skills 结构**：
```json
{
  "webSearch": {
    "enabled": true,
    "apiKey": "${TAVILY_API_KEY}",
    "maxResults": 5
  },
  "subsidyCalculator": {
    "enabled": true
  },
  "fileParser": {
    "enabled": true
  }
}
```

| 技能 | 说明 | 配置项 |
|------|------|--------|
| webSearch | 联网搜索工具 | enabled, apiKey, maxResults |
| subsidyCalculator | 补贴计算工具 | enabled |
| fileParser | 文件解析工具 | enabled |

#### FR-12: MCP 服务器配置

| 字段 | 类型 | 说明 |
|------|------|------|
| mcp_servers_config | JSONB | MCP 服务器配置列表 |

**mcp_servers_config 结构**：
```json
[
  {
    "name": "filesystem",
    "enabled": true,
    "url": "stdio://python -m mcp_server_stdio",
    "timeout": 30000
  }
]
```

| 配置项 | 类型 | 说明 |
|--------|------|------|
| name | String | MCP 服务器名称 |
| enabled | Boolean | 是否启用 |
| url | String | MCP 服务器连接 URL |
| timeout | Integer | 超时时间（毫秒） |

---

### 3.4 实时预览

#### FR-13: 配置预览

| 需求描述 | 详细说明 |
|----------|----------|
| 触发条件 | 左侧配置面板修改任何字段 |
| 表现形式 | 右侧预览区实时更新 |
| 预览内容 | 展示当前配置的关键信息（名称、模型、提示词摘要等） |

#### FR-14: 聊天测试

| 需求描述 | 详细说明 |
|----------|----------|
| 接口 | `POST /api/admin/agent-config/test` |
| 功能 | 使用当前配置发起测试对话，验证配置是否正确 |
| 独立性 | 测试对话不影响主系统的 ChatMemory |

#### FR-15: 配置 JSON 展示

| 需求描述 | 详细说明 |
|----------|----------|
| 功能 | 以格式化的 JSON 展示当前配置 |
| 用途 | 便于管理员查看完整配置，支持复制 |

---

### 3.5 前端界面

#### FR-16: 界面布局

| 区域 | 尺寸 | 内容 |
|------|------|------|
| TopNavbar | 固定顶部 | 现有导航栏，添加"管理员"入口 |
| 配置面板（左侧） | 宽度 420px | 配置表单 |
| 预览面板（右侧） | 剩余空间 | 实时预览、聊天测试等 |

#### FR-17: 配置面板组件

| 组件 | 功能 |
|------|------|
| 智能体名称输入 | 文本输入框 |
| 描述输入 | 文本域 |
| 模型选择 | 下拉选择 + 自定义输入 |
| 系统提示词 | 多行文本域 |
| 技能模块 | 复选框 + 展开配置 |
| 开场白 | 单行文本输入 |
| 保存按钮 | 提交配置到后端 |
| 重置按钮 | 恢复到上一次保存的状态 |

#### FR-18: 预览面板组件

| 组件 | 功能 |
|------|------|
| 标签页导航 | 切换预览模式 |
| 聊天测试区 | 测试配置的实际效果 |
| 配置 JSON 展示 | 格式化展示配置 |

---

## 4. 非功能性需求

### 4.1 安全性

| 需求 | 说明 |
|------|------|
| NFR-1 | 所有管理员接口必须验证 JWT Token |
| NFR-2 | 所有管理员接口必须验证用户角色为 ADMIN |
| NFR-3 | 修改密码时验证旧密码的正确性 |
| NFR-4 | 敏感信息（API Key）在展示时脱敏 |
| NFR-5 | 管理员操作日志记录 |

### 4.2 性能

| 需求 | 说明 |
|------|------|
| NFR-6 | 配置更新在 500ms 内完成数据库持久化 |
| NFR-7 | 配置同步在 1s 内完成运行时更新 |
| NFR-8 | 预览区实时更新延迟 < 100ms |

### 4.3 可用性

| 需求 | 说明 |
|------|------|
| NFR-9 | 配置修改提供"撤销"功能（重置到保存状态） |
| NFR-10 | 错误提示清晰、具体 |
| NFR-11 | 配置验证在提交前进行，提前发现错误 |

### 4.4 兼容性

| 需求 | 说明 |
|------|------|
| NFR-12 | 前端界面风格与现有页面完全一致 |
| NFR-13 | 响应式设计，支持移动端访问 |

---

## 5. 数据模型设计

### 5.1 数据库表结构

#### agent_config 表

```sql
CREATE TABLE agent_config (
    id BIGSERIAL PRIMARY KEY,

    -- 基础信息
    name VARCHAR(100) NOT NULL DEFAULT '政策问答智能体',
    description TEXT DEFAULT '用于山东省以旧换新补贴政策咨询',

    -- AI 模型配置
    model_provider VARCHAR(50) NOT NULL DEFAULT 'dashscope',
    api_key VARCHAR(255) DEFAULT '${DASHSCOPE_API_KEY}',
    api_url VARCHAR(255) DEFAULT 'https://dashscope.aliyuncs.com/compatible-mode',
    model_name VARCHAR(100) NOT NULL DEFAULT 'qwen3.5-plus',
    temperature DECIMAL(3,2) DEFAULT 0.70,

    -- 系统提示词
    system_prompt TEXT NOT NULL,

    -- 开场白
    greeting_message TEXT DEFAULT '您好！我是山东省以旧换新政策咨询智能助手。您可以问我关于汽车、家电、数码产品等的补贴标准和申请流程。

**我可以帮您：**
- 查询各类产品补贴金额
- 了解申请条件和流程
- 计算您能获得的补贴
- 解答政策相关疑问',

    -- 技能模块配置
    skills JSONB NOT NULL DEFAULT '{
        "webSearch": {"enabled": true},
        "subsidyCalculator": {"enabled": true},
        "fileParser": {"enabled": true}
    }'::jsonb,

    -- MCP 服务器配置
    mcp_servers_config JSONB DEFAULT '[]'::jsonb,

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- 唯一约束（确保只有一个配置）
    CONSTRAINT single_agent CHECK (id = 1)
);
```

### 5.2 JPA 实体

```java
@Entity
@Table(name = "agent_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {
    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false, length = 50)
    private String modelProvider;

    @Column(length = 255)
    private String apiKey;

    @Column(length = 255)
    private String apiUrl;

    @Column(nullable = false, length = 100)
    private String modelName;

    @Column
    private Double temperature;

    @Lob
    @Column(nullable = false)
    private String systemPrompt;

    @Lob
    @Column
    private String greetingMessage;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> skills;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private List<McpServerConfig> mcpServersConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

---

## 6. API 设计

### 6.1 管理员认证 API

#### POST /api/admin/auth/login

管理员登录接口

**请求体**：
```json
{
  "username": "admin",
  "password": "admin"
}
```

**响应体**：
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "expiresIn": 86400000
}
```

**错误响应**：
```json
{
  "error": "INVALID_CREDENTIALS",
  "message": "用户名或密码错误"
}
```

#### POST /api/admin/auth/change-password

修改管理员密码

**请求体**：
```json
{
  "oldPassword": "admin",
  "newPassword": "newpassword",
  "confirmPassword": "newpassword"
}
```

**响应体**：204 No Content

### 6.2 智能体配置 API

#### GET /api/admin/agent-config

获取当前智能体配置

**响应体**：
```json
{
  "id": 1,
  "name": "政策问答智能体",
  "description": "用于山东省以旧换新补贴政策咨询",
  "modelProvider": "dashscope",
  "apiKey": "${DASHSCOPE_API_KEY}",
  "apiUrl": "https://dashscope.aliyuncs.com/compatible-mode",
  "modelName": "qwen3.5-plus",
  "temperature": 0.7,
  "systemPrompt": "你是一个专业的...",
  "greetingMessage": "您好！我是山东省以旧换新政策咨询智能助手...",
  "skills": {
    "webSearch": {"enabled": true},
    "subsidyCalculator": {"enabled": true},
    "fileParser": {"enabled": true}
  },
  "mcpServersConfig": [],
  "createdAt": "2026-03-02T10:00:00Z",
  "updatedAt": "2026-03-02T10:00:00Z"
}
```

#### PUT /api/admin/agent-config

更新智能体配置

**请求体**：
```json
{
  "name": "政策问答智能体",
  "description": "用于山东省以旧换新补贴政策咨询",
  "modelProvider": "dashscope",
  "apiKey": "${DASHSCOPE_API_KEY}",
  "apiUrl": "https://dashscope.aliyuncs.com/compatible-mode",
  "modelName": "qwen3-max",
  "temperature": 0.8,
  "systemPrompt": "你是一个专业的...",
  "greetingMessage": "您好！我是山东省以旧换新政策咨询智能助手...",
  "skills": {
    "webSearch": {"enabled": true},
    "subsidyCalculator": {"enabled": true},
    "fileParser": {"enabled": false}
  },
  "mcpServersConfig": []
}
```

**响应体**：返回更新后的配置（同 GET 响应）

#### POST /api/admin/agent-config/test

测试配置

**请求体**：
```json
{
  "message": "测试消息",
  "sessionId": "test-session-id"
}
```

**响应体**：
```json
{
  "message": "AI 回复内容",
  "references": [],
  "toolCalls": []
}
```

#### POST /api/admin/agent-config/reset

重置配置为默认值

**响应体**：返回默认配置（同 GET 响应）

---

## 7. 系统架构

### 7.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端层 (React 19)                     │
│  ┌──────────────┐  ┌────────────────────────────────────┐ ares│
│  │ 登录页面    │  │        管理控制台                    │   │
│  │ LoginPage  │  │  ┌────────────┬──────────────────┐  │  │
│  └──────────────┘  │  │ 配置面板   │  预览面板        │  │  │
│                   │  │  (左侧)    │  (右侧)          │  │  │
│                   │  └────────────┴──────────────────┘  │  │
│                   └────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                            ↓ JWT
┌─────────────────────────────────────────────────────────────────┐
│                        后端层 (Spring Boot 3.4)             │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  SecurityFilterChain (JWT + ADMIN Role Check)          │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐ │ │
│  │ AdminAuth   │  │ AgentConfig  │  │ ConfigSync  │ │ │
│  │ Controller  │  │ Controller  │  │ Service    │ │ │
│  └──────────────┘  └──────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                        数据层                               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  PostgreSQL (users 表 + agent_config 表)             │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 配置同步机制

```
┌─────────────────────────────────────────────────────────────────┐
│              配置更新到运行时的完整流程                       │
└─────────────────────────────────────────────────────────────────┘

1. 前端用户在左侧修改配置
   ↓
2. 点击"保存"按钮
   ↓
3. PUT /api/admin/agent-config
   ↓
4. AgentConfigController.updateConfig() 接收请求
   ↓
5. 保存到 agent_config 表（持久化）
   ↓
6. 调用 AgentConfigSyncService.syncConfigToRuntime()
   ↓
7. syncConfigToRuntime() 执行：
   a) 读取最新的 agent_config 记录
   b) 更新 ChatClientConfig 中的 ChatModel 配置
   c) 更新各工具的 enabled 状态
      - WebSearchTool.setEnabled()
      - SubsidyCalculatorTool.setEnabled()
      - FileParserTool.setEnabled()
   d) 更新 MCP Client 的服务器配置
      - 重连或更新 MCP 服务器
   ↓
8. 配置立即生效，后续用户请求使用新配置
```

---

## 8. 前端设计规范

### 8.1 样式规范

为确保与现有界面风格完全一致，需严格遵循以下规范：

| 规范项 | 说明 |
|--------|------|
| CSS 变量 | 复用 `variables.css` 中定义的变量 |
| 主色调 | `hsl(var(--color-primary))` |
| 背景色 | `hsl(var(--color-bg-elevated))` |
| 边框色 | `hsl(var(--color-border))` |
| 阴影 | `var(--shadow-lg)` |
| 圆角 | `var(--radius-lg)` |
| 玻璃态效果 | `backdrop-filter: blur(var(--glass-blur))` |
| 字体 | `font-family: var(--font-sans)` |

### 8.2 组件样式示例

```css
.admin-console-container {
  display: flex;
  min-height: calc(100vh - 72px);
  background: hsl(var(--color-bg-soft));
}

.config-panel {
  width: 420px;
  background: hsl(var(--color-bg-elevated));
  border-right: 1px solid hsl(var(--color-border));
}

.panel-header {
  padding: var(--space-md);
  border-bottom: 1px solid hsl(var(--color-border));
  font-size: 1.1rem;
  font-weight: 600;
}

.form-section {
  padding: var(--space-md);
}

.input-group input,
.input-group textarea {
  padding: 12px;
  border: 1px solid hsl(var(--color-border-soft));
  border-radius: var(--radius-md);
  background: hsl(var(--color-bg-soft));
}

.primary-btn {
  background: linear-gradient(135deg, hsl(var(--color-primary)), hsl(var(--color-primary-hover)));
  color: hsl(var(--color-text-inverse));
  border-radius: var(--radius-pill);
}
```

### 8.3 目录结构

```
frontend/src/
├── pages/
│   └── AdminConsolePage.jsx       # 管理控制台主页面
│   └── AdminConsolePage.css       # 管理控制台样式
├── components/
│   └── admin/
│       ├── ConfigPanel.jsx         # 左侧配置面板
│       ├── ConfigPanel.css
│       ├── PreviewPanel.jsx       # 右侧预览面板
│       ├── PreviewPanel.css
│       ├── ChatTestTab.jsx        # 聊天测试标签页
│       ├── ConfigJsonTab.jsx      # 配置JSON标签页
│       └── EffectPreviewTab.jsx    # 效果预览标签页
├── services/
│   └── adminApi.js               # 管理员API服务
└── context/
    └── AuthContext.jsx           # 扩展管理员状态
```

---

## 9. 测试策略

### 9.1 后端测试

#### 单元测试

| 测试类 | 测试内容 |
|--------|----------|
| AdminAuthControllerTest | 管理员登录、修改密码 |
| AgentConfigControllerTest | 配置 CRUD 操作 |
| AgentConfigSyncServiceTest | 配置同步逻辑 |
| AdminInitializerTest | 管理员初始化 |

#### 集成测试

| 测试场景 | 测试内容 |
|----------|----------|
| 配置更新流程 | 前端提交 → 数据库持久化 → 运行时同步 |
| MCP 动态连接 | 添加 MCP 服务器 → 验证连接 |
| 权限控制 | 非管理员访问被拒绝 |

### 9.2 前端测试

| 测试类型 | 测试内容 |
|----------|----------|
| 组件测试 | 配置面板、预览面板组件渲染和交互 |
| API 测试 | adminApi 各方法的正确调用 |
| 集成测试 | 完整的配置更新和预览流程 |

### 9.3 手动测试

| 测试场景 | 预期结果 |
|----------|----------|
| 修改模型名称 | 配置保存，后续对话使用新模型 |
| 修改系统提示词 | 智能体行为符合新提示词 |
| 禁用技能模块 | 对应工具不再被调用 |
| 添加 MCP 服务器 | MCP 服务器成功连接 |

---

## 10. 实施计划

### 10.1 阶段划分

#### 阶段 1：后端基础（预计 3 天）

| 任务 | 负责人 | 状态 |
|------|--------|------|
| 创建 agent_config 表 | - | 待开始 |
| 创建 AgentConfig 实体类 | - | 待开始 |
| 实现 AdminInitializer | - | 待开始 |
| 实现 AdminAuthController | - | 待开始 |
| 实现 AgentConfigController | - | 待开始 |

#### 阶段 2：配置同步（预计 2 天）

| 任务 | 负责人 | 状态 |
|------|--------|------|
| 实现 AgentConfigSyncService | - | 待开始 |
| 实现 ChatClientConfig 动态更新 | - | 待开始 |
| 实现工具状态动态更新 | - | 待开始 |

#### 阶段 3：前端开发（预计 4 天）

| 任务 | 负责人 | 状态 |
|------|--------|------|
| 创建 AdminConsolePage 组件 | - | 待开始 |
| 创建 ConfigPanel 组件 | - | 待开始 |
| 创建 PreviewPanel 组件 | - | 待开始 |
| 实现 adminApi 服务 | - | 待开始 |
| 样式开发 | - | 待开始 |

#### 阶段 4：测试与优化（预计 2 天）

| 任务 | 负责人 | 状态 |
|------|--------|------|
| 单元测试编写 | - | 待开始 |
| 集成测试编写 | - | 待开始 |
| 手动测试 | - | 待开始 |
| Bug 修复 | - | 待开始 |

### 10.2 总体进度

| 阶段 | 进度 |
|------|------|
| 阶段 1：后端基础 | 0% |
| 阶段 2：配置同步 | 0% |
| 阶段 3：前端开发 | 0% |
| 阶段 4：测试与优化 | 0% |
| **总体进度** | **0%** |

---

## 11. 风险与依赖

### 11.1 风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 配置同步失败导致系统异常 | 高 | 实现回滚机制，保留上次有效配置 |
| MCP 动态连接不稳定 | 中 | 提供连接状态检查和错误提示 |
| 前端状态管理复杂 | 低 | 使用 React Context 或状态管理库 |

### 11.2 依赖

| 依赖 | 说明 |
|------|------|
| PostgreSQL | 数据库支持 |
| Spring Security | 权限控制 |
| Spring AI MCP Client | MCP 功能支持 |
| React Router | 路由管理 |

---

## 12. 附录

### 12.1 参考文档

- Spring Security 官方文档
- Spring AI MCP Client 文档
- React 官方文档

### 12.2 变更历史

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|----------|------|
| v1.1 | 2026-03-02 | 新增 api_key、api api_url 字段；更新默认开场白内容 | Claude |
| v1.0 | 2026-03-02 | 初始版本 | Claude |

---

**文档结束**
