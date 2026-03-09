# 模型服务管理功能 PRD

| 项目 | 内容 |
|------|------|
| 文档名称 | 模型服务管理 - 管理员控制台第三方模型接入 |
| 创建日期 | 2026-03-07 |
| 版本 | v1.0 |
| 状态 | 已评审 |

---

## 1. 项目概述

### 1.1 背景

山东省智能政策咨询助手目前仅支持单一模型配置（阿里云 DashScope）。为满足业务需求，需支持管理员接入第三方国内模型服务商，实现多模型管理和灵活切换。

### 1.2 目标

在管理员控制台新增「模型」菜单，包含4个子Tab：

1. **大语言模型** - 对应智能体对话能力
2. **视觉模型** - 对应 `/api/multimodal/analyze-image` 图像分析
3. **语音模型** - 对应 `/api/multimodal/transcribe` 语音识别
4. **嵌入模型** - 对应向量嵌入能力

支持功能：

- 每种类型支持添加/编辑/删除多个模型配置
- 支持的国内厂商：DeepSeek、硅基流动、阿里云百炼、智谱AI、kimi、魔搭社区、火山引擎
- 每种类型独立维护默认模型
- 支持模型连接测试
- 在「智能体」相关设置中可通过下拉菜单选择已配置模型

### 1.3 范围

- 新增模型管理 Tab 及后端 API
- 改造智能体基础设置，集成模型选择
- 预留多模态模型选择接口（待后续实现）

---

## 2. 需求决策记录

| 需求项 | 决策 |
|--------|------|
| 模型类型 | 4种：大语言模型、视觉模型、语音模型、嵌入模型 |
| 国内厂商支持 | DeepSeek、硅基流动、阿里云百炼、智谱AI、kimi、魔搭社区、火山引擎 |
| 参数模板 | 统一参数模板，按类型显示/隐藏相关字段 |
| 默认模型 | 每种类型独立维护默认模型 |
| 智能体关联 | 下拉选择已配置模型 |
| 测试功能 | 支持测试连接验证配置正确性 |
| 多模型支持 | 是，每种类型支持配置多个模型 |

---

## 3. 系统架构

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         前端层 (React)                               │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │              AdminConsolePage                                  │ │
│  │  ┌──────────────────────────────────────────────────────────┐ │ │
│  │  │  [智能体] [知识库] [工具] [模型]                         │ │ │
│  │  └──────────────────────────────────────────────────────────┘ │ │
│  │  ┌──────────────────────────────────────────────────────────┐ │ │
│  │  │                    模型 Tab                              │ │ │
│  │  │  [大语言模型] [视觉模型] [语音模型] [嵌入模型]           │ │ │
│  │  │  ┌────────────────────────────────────────────────────┐  │ │ │
│  │  │  │  模型列表表格                                       │  │ │ │
│  │  │  │  - 模型名称 / 服务商 / API / 状态 / 操作           │  │ │ │
│  │  │  │  [+ 新增模型]                                       │  │ │ │
│  │  │  └────────────────────────────────────────────────────┘  │ │ │
│  │  └──────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────────┐
│                    后端层 (Spring Boot)                              │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │              ModelProviderController                           │ │
│  │  GET    /api/admin/models           (获取模型列表)            │ │
│  │  GET    /api/admin/models/{id}      (获取模型详情)            │ │
│  │  POST   /api/admin/models           (新增模型)                │ │
│  │  PUT    /api/admin/models/{id}      (更新模型)                │ │
│  │  DELETE /api/admin/models/{id}      (删除模型)                │ │
│  │  POST   /api/admin/models/{id}/test (测试连接)                │ │
│  │  PUT    /api/admin/models/{id}/set-default (设为默认)         │ │
│  │  GET    /api/admin/models/options   (下拉选项)                │ │
│  └───────────────────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │              AgentConfigController (已改造)                   │ │
│  │  GET/PUT /api/admin/agent-config  (支持选择自定义模型)        │ │
│  └───────────────────────────────────────────────────────────────┘ │
│  ┌──────────────────┐  ┌──────────────────┐                      │
│  │ModelProviderServ │  │ ChatClientConfig │                      │
│  │  - 模型 CRUD     │  │  - 动态加载 LLM  │                      │
│  │  - 测试连接      │  │  - 配置注入      │                      │
│  │  - 选项列表      │  └──────────────────┘                      │
│  └──────────────────┘                                            │
└─────────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────────┐
│                        数据层                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │  PostgreSQL  │  │    Redis     │  │      MinIO             │ │
│  │              │  │              │  │                        │ │
│  │  - agent_    │  │  - 会话缓存  │  │  - 文档存储            │ │
│  │    config    │  │              │  │                        │ │
│  │  - model_    │  │              │  │                        │ │
│  │    provider  │  │              │  │                        │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. 数据模型设计

### 4.1 数据库表结构

#### model_provider 表（模型服务商）

```sql
CREATE TABLE model_provider (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,              -- 模型名称（显示用）
    type VARCHAR(20) NOT NULL,               -- 模型类型：LLM/VISION/AUDIO/EMBEDDING
    provider VARCHAR(50) NOT NULL,           -- 服务商标识
    api_url VARCHAR(500) NOT NULL,           -- API 地址
    api_key VARCHAR(500) NOT NULL,           -- API 密钥（加密存储）
    model_name VARCHAR(100) NOT NULL,        -- 具体模型名称
    temperature DECIMAL(3,2) DEFAULT 0.7,    -- 温度参数
    max_tokens INTEGER,                      -- 最大Token数
    top_p DECIMAL(5,4),                      -- TopP
    is_default BOOLEAN DEFAULT FALSE,        -- 是否默认
    is_enabled BOOLEAN DEFAULT TRUE,         -- 是否启用
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT                        -- 创建人（管理员ID）
);

CREATE INDEX idx_model_provider_type ON model_provider(type);
CREATE INDEX idx_model_provider_provider ON model_provider(provider);
CREATE INDEX idx_model_provider_is_default ON model_provider(type, is_default);
```

### 4.2 JPA 实体类

**ModelProvider.java**

```java
@Entity
@Table(name = "model_provider")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProvider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ModelType type;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "api_url", nullable = false, length = 500)
    private String apiUrl;

    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal temperature = new BigDecimal("0.7");

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(precision = 5, scale = 4)
    private BigDecimal topP;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

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

public enum ModelType {
    LLM,       // 大语言模型
    VISION,    // 视觉模型
    AUDIO,     // 语音模型
    EMBEDDING  // 嵌入模型
}
```

---

## 5. API 设计

### 5.1 模型管理 API

#### GET /api/admin/models
获取模型列表（支持 type 过滤）

**Query Params:**
- type: LLM | VISION | AUDIO | EMBEDDING (可选)

**响应体：**
```json
{
  "models": [
    {
      "id": 1,
      "name": "DeepSeek Chat",
      "type": "LLM",
      "provider": "deepseek",
      "apiUrl": "https://api.deepseek.com/v1",
      "modelName": "deepseek-chat",
      "temperature": 0.7,
      "maxTokens": 4096,
      "topP": 0.9,
      "isDefault": true,
      "isEnabled": true,
      "createdAt": "2026-03-07T10:00:00Z"
    }
  ]
}
```

#### GET /api/admin/models/{id}
获取单个模型详情

#### POST /api/admin/models
新增模型

**请求体：**
```json
{
  "name": "DeepSeek Chat",
  "type": "LLM",
  "provider": "deepseek",
  "apiUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-xxxxx",
  "modelName": "deepseek-chat",
  "temperature": 0.7,
  "maxTokens": 4096,
  "topP": 0.9,
  "isDefault": true,
  "isEnabled": true
}
```

#### PUT /api/admin/models/{id}
更新模型

#### DELETE /api/admin/models/{id}
删除模型

#### POST /api/admin/models/{id}/test
测试模型连接

**响应体：**
```json
{
  "success": true,
  "message": "连接成功",
  "latencyMs": 120
}
```

或失败：
```json
{
  "success": false,
  "message": "API Key 无效",
  "error": "invalid_api_key"
}
```

#### PUT /api/admin/models/{id}/set-default
设为默认模型（同一类型只能有一个默认）

#### GET /api/admin/models/options
获取模型选项列表（下拉选择用，按 type 分组）

**响应体：**
```json
{
  "LLM": [
    { "id": 1, "name": "DeepSeek Chat", "isDefault": true },
    { "id": 2, "name": "硅基流动 Qwen", "isDefault": false }
  ],
  "VISION": [...],
  "AUDIO": [...],
  "EMBEDDING": [...]
}
```

---

## 6. 前端设计

### 6.1 组件结构

```
frontend/src/components/admin/
├── ModelsTab.jsx           # 模型管理父Tab组件（含4个子Tab）
├── ModelListPanel.jsx      # 通用模型列表组件（支持type参数）
├── ModelFormModal.jsx      # 新增/编辑模型弹窗
├── ModelTestModal.jsx      # 测试连接弹窗（可选，合并到FormModal）
├── AdminNavbar.jsx         # 修改：添加「模型」菜单项
├── ConfigPanel.jsx         # 修改：基础设置改为下拉选择
├── KnowledgeBaseTab.jsx    # 已存在
├── ToolsTab.jsx            # 已存在
└── ConfigPanel.jsx         # 已存在

frontend/src/services/
└── adminApi.js             # 修改：添加模型管理API方法
```

### 6.2 主界面布局

```
┌─────────────────────────────────────────────────────────────────┐
│  [智能体] [知识库] [工具] [模型]                                  │
├─────────────────────────────────────────────────────────────────┤
│  模型                                                              │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ [大语言模型] [视觉模型] [语音模型] [嵌入模型]               ││
│  ├─────────────────────────────────────────────────────────────┤│
│  │                                                             ││
│  │  模型名称        服务商        API               状态  操作  ││
│  │  ─────────────────────────────────────────────────────────  ││
│  │  DeepSeek      DeepSeek      api.deepseek...   启用  ⋮      ││
│  │  硅基流动       硅基流动      api.siliconflow.. 启用  ⋮      ││
│  │                                                             ││
│  │                                           [+ 新增模型]      ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 6.3 新增/编辑模型弹窗

```
┌─────────────────────────────────────────────┐
│           新增/编辑模型               [X]    │
├─────────────────────────────────────────────┤
│                                              │
│  模型名称: [_______________]                 │
│                                              │
│  模型类型: [大语言模型 ▼]                    │
│                                              │
│  服务商:   [DeepSeek ▼]  (或 [自定义])      │
│                                              │
│  API 地址: [___________________________]    │
│            https://api.deepseek.com/v1      │
│                                              │
│  API Key:  [___________________________]    │
│            ●●●●●●●●●●●●●●●●●●●●             │
│                                              │
│  模型名称: [_______________]                 │
│            deepseek-chat                    │
│                                              │
│  ──── 高级参数（可选） ────                  │
│                                              │
│  温度:     [0.7]  (0.0 - 1.0)               │
│  最大Token:[4096]                            │
│  TopP:     [0.9]                             │
│                                              │
│  ☑ 设为默认模型                              │
│  ☑ 启用                                      │
│                                              │
│       [取消]  [测试连接]  [保存]            │
└─────────────────────────────────────────────┘
```

### 6.4 智能体基础设置改造

```
┌─────────────────────────────────────────────┐
│  基础设置                                    │
├─────────────────────────────────────────────┤
│                                              │
│  模型: [系统默认（DashScope）▼]              │
│        ┌────────────────────────┐            │
│        │ 系统默认（DashScope）  │            │
│        │ DeepSeek Chat          │            │
│        │ 硅基流动 Qwen          │            │
│        └────────────────────────┘            │
│                                              │
│  当选择「系统默认」时，显示手动输入：         │
│                                              │
│  API Key (脱敏): [_______________]           │
│  温度 (Temperature: 0.0 - 1.0): [0.7]        │
│                                              │
└─────────────────────────────────────────────┘
```

---

## 7. 服务商配置模板

### 7.1 默认 API 地址模板

| 服务商 | API 地址模板 | 默认模型 |
|--------|--------------|----------|
| DeepSeek | https://api.deepseek.com/v1 | deepseek-chat |
| 硅基流动 | https://api.siliconflow.cn/v1 | Qwen/Qwen2.5-7B-Instruct |
| 阿里云百炼 | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen-plus |
| 智谱AI | https://open.bigmodel.cn/api/paas/v4 | glm-4 |
| kimi | https://api.moonshot.cn/v1 | moonshot-v1-8k |
| 魔搭社区 | https://.modelscope.cn/v1 | qwen/Qwen2-7B-Instruct |
| 火山引擎 | https://ark.cn-beijing.volces.com/api/v3 | doubao-pro-4k |
| 自定义 | (空，用户输入) | (空，用户输入) |

---

## 8. 实施计划

### 阶段 1：后端基础设施（预计 1 天）

- [ ] 创建 ModelProvider 实体类
- [ ] 创建 ModelProviderRepository 数据访问层
- [ ] 创建 ModelProviderRequest/Response DTO
- [ ] 更新 docker-compose.yml 添加数据库表（如需要）

### 阶段 2：后端核心服务（预计 2 天）

- [ ] 创建 ModelProviderService 业务服务层
- [ ] 实现模型 CRUD 功能
- [ ] 实现测试连接功能
- [ ] 实现设为默认功能
- [ ] 实现加密存储（API Key）
- [ ] 创建 ModelProviderController

### 阶段 3：后端集成（预计 1 天）

- [ ] 修改 AgentConfigService，支持从 model_provider 加载配置
- [ ] 修改 ChatClientConfig，支持动态加载 LLM 配置
- [ ] 预留多模态模型加载接口

### 阶段 4：前端开发（预计 2-3 天）

- [ ] 修改 AdminNavbar 添加「模型」菜单项
- [ ] 修改 AdminConsolePage 处理 models Tab
- [ ] 新增 ModelsTab 组件（含4个子Tab）
- [ ] 新增 ModelListPanel 组件
- [ ] 新增 ModelFormModal 组件
- [ ] 修改 ConfigPanel：基础设置改为下拉选择
- [ ] 对接 API

### 阶段 5：测试与优化（预计 1 天）

- [ ] 完整功能测试
- [ ] 集成测试（实际调用模型）
- [ ] 错误处理优化
- [ ] 用户体验调整

**总体预计：7-8 天**

---

## 9. 验证方案

### 9.1 功能验证

1. 访问管理员控制台，确认新增「模型」Tab 可见
2. 点击「模型」Tab，验证4个子Tab切换正常
3. 在「视觉模型」子Tab测试新增模型功能
4. 测试各子Tab的编辑、删除、设为默认功能
5. 测试「测试连接」功能（不同类型发送不同测试请求）
6. 切换到「智能体」Tab：
   - 基础设置中确认 LLM 模型下拉可选
   - 多模态设置中确认视觉/语音模型下拉可选
7. 选择自定义模型，确认配置正确填充

### 9.2 接口验证

- 使用 curl 测试各 API 端点返回正确

### 9.3 集成验证

- 完成配置后，实际发起对话验证模型调用成功
- 测试多模态功能（图像分析、语音识别）调用成功

---

## 10. 关键文件清单

### 后端（新增）

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/com/shandong/policyagent/entity/ModelProvider.java` | 实体类 |
| `backend/src/main/java/com/shandong/policyagent/entity/ModelType.java` | 枚举类 |
| `backend/src/main/java/com/shandong/policyagent/repository/ModelProviderRepository.java` | Repository |
| `backend/src/main/java/com/shandong/policyagent/service/ModelProviderService.java` | 服务层 |
| `backend/src/main/java/com/shandong/policyagent/controller/ModelProviderController.java` | 控制器 |
| `backend/src/main/java/com/shandong/policyagent/dto/ModelProviderRequest.java` | 请求DTO |
| `backend/src/main/java/com/shandong/policyagent/dto/ModelProviderResponse.java` | 响应DTO |

### 后端（修改）

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/com/shandong/policyagent/service/AgentConfigService.java` | 加载自定义模型配置 |
| `backend/src/main/java/com/shandong/policyagent/config/ChatClientConfig.java` | 动态加载LLM |

### 前端（新增）

| 文件 | 说明 |
|------|------|
| `frontend/src/components/admin/ModelsTab.jsx` | 模型管理父Tab |
| `frontend/src/components/admin/ModelListPanel.jsx` | 模型列表 |
| `frontend/src/components/admin/ModelFormModal.jsx` | 新增/编辑弹窗 |

### 前端（修改）

| 文件 | 说明 |
|------|------|
| `frontend/src/components/admin/AdminNavbar.jsx` | 添加菜单项 |
| `frontend/src/pages/AdminConsolePage.jsx` | 处理models Tab |
| `frontend/src/components/admin/ConfigPanel.jsx` | 下拉选择 |
| `frontend/src/services/adminApi.js` | 添加API方法 |

---

## 11. 附录

### 11.1 支持的国内服务商

| 服务商 | 标识符 | API 文档 |
|--------|--------|----------|
| DeepSeek | deepseek | https://platform.deepseek.com/docs |
| 硅基流动 | siliconflow | https://siliconflow.cn/docs |
| 阿里云百炼 | dashscope | https://help.aliyun.com/zh/model-studio/developer-reference/compatibility-of-openai-with-dashscope |
| 智谱AI | zhipuai | https://open.bigmodel.cn/doc/api |
| kimi | moonshot | https://platform.moonshot.cn/docs |
| 魔搭社区 | modelscope | https://modelscope.cn/docs |
| 火山引擎 | volcano | https://www.volcengine.com/docs/ark |

### 11.2 字段说明

| 字段 | 说明 |
|------|------|
| name | 自定义名称，用于在列表和下拉中显示 |
| type | 模型类型，决定在哪个子Tab显示 |
| provider | 服务商标识，用于API地址模板填充 |
| apiUrl | 完整API地址，含协议和路径 |
| apiKey | 服务商提供的API密钥，将加密存储 |
| modelName | 具体模型名称，如 deepseek-chat |
| temperature | 采样温度，影响输出随机性 |
| maxTokens | 最大输出Token数 |
| topP | 核采样参数 |
| isDefault | 是否为该类型的默认模型 |
| isEnabled | 是否启用，禁用后不可选择 |

---

**文档结束**