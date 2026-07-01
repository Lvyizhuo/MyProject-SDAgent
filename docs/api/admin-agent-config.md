# 智能体配置模块

> 管理员对智能体运行时配置的查看、更新、重置与测试。

- **路径前缀**：`/api/admin/agent-config`
- **认证**：管理员（`ADMIN` 角色）
- **控制器**：`AgentConfigController`、`AgentConfigTestController`

## 接口一览

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/admin/agent-config` | 获取当前配置（API Key 脱敏） |
| PUT | `/api/admin/agent-config` | 更新配置并同步到运行时 |
| POST | `/api/admin/agent-config/reset` | 重置为默认配置并同步运行时 |
| POST | `/api/admin/agent-config/test` | 测试当前配置是否能正常对话 |

---

## 1. 获取当前配置

返回当前智能体配置，API Key 已脱敏。

### 请求

```
GET /api/admin/agent-config
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

**响应体**（`AgentConfigResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 配置 ID |
| `name` | String | 智能体名称 |
| `description` | String | 描述 |
| `modelProvider` | String | 模型提供商 |
| `apiKey` | String | API Key（脱敏） |
| `apiUrl` | String | API URL |
| `modelName` | String | 模型名称 |
| `temperature` | Double | 温度值 |
| `llmModelId` | Long | 大语言模型 ID |
| `visionModelId` | Long | 视觉模型 ID |
| `audioModelId` | Long | 语音模型 ID |
| `embeddingModelId` | Long | 嵌入模型 ID |
| `knowledgeBaseId` | Long | 知识库 ID |
| `knowledgeBaseName` | String | 知识库名称 |
| `knowledgeBasePath` | String | 知识库路径 |
| `knowledgeBaseEmbeddingModel` | String | 知识库嵌入模型 |
| `knowledgeBaseFolderId` | Long | 知识库文件夹 ID |
| `knowledgeBaseFolderName` | String | 知识库文件夹名称 |
| `knowledgeBaseFolderPath` | String | 知识库文件夹路径 |
| `effectiveConfigSource` | String | 生效配置来源 |
| `effectiveModelProvider` | String | 生效模型提供商 |
| `effectiveApiUrl` | String | 生效 API URL |
| `effectiveModelName` | String | 生效模型名称 |
| `effectiveTemperature` | Double | 生效温度值 |
| `effectiveMaxTokens` | Integer | 生效最大 Token 数 |
| `effectiveTopP` | BigDecimal | 生效 TopP |
| `resolvedLlmModel` | ResolvedModelSnapshot | 解析后的 LLM 快照 |
| `resolvedVisionModel` | ResolvedModelSnapshot | 解析后的视觉模型快照 |
| `resolvedAudioModel` | ResolvedModelSnapshot | 解析后的语音模型快照 |
| `resolvedEmbeddingModel` | ResolvedModelSnapshot | 解析后的嵌入模型快照 |
| `systemPrompt` | String | 系统提示词 |
| `greetingMessage` | String | 开场白 |
| `skills` | Map\<String, Object\> | 技能配置 |
| `mcpServersConfig` | List\<Map\<String, Object\>\> | MCP 服务器配置 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |

**ResolvedModelSnapshot（模型快照）字段：**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 模型 ID |
| `name` | String | 模型名称 |
| `provider` | String | 服务商 |
| `apiUrl` | String | API URL |
| `modelName` | String | 具体模型名称 |
| `temperature` | Double | 温度参数 |
| `maxTokens` | Integer | 最大 Token 数 |
| `topP` | BigDecimal | TopP 参数 |
| `isDefault` | Boolean | 是否默认 |
| `isEnabled` | Boolean | 是否启用 |

---

## 2. 更新配置

更新智能体配置，并立即同步到运行时（无需重启）。

### 请求

```
PUT /api/admin/agent-config
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`AgentConfigRequest`）：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `name` | String | 是 | 长度 ≤ 100 | 智能体名称 |
| `description` | String | 否 | 长度 ≤ 500 | 描述 |
| `modelProvider` | String | 是 | 长度 ≤ 50 | 模型提供商 |
| `apiKey` | String | 否 | 长度 ≤ 255 | API Key |
| `apiUrl` | String | 否 | 长度 ≤ 255 | API URL |
| `modelName` | String | 是 | 长度 ≤ 100 | 模型名称 |
| `temperature` | Double | 否 | 0.0 ~ 1.0 | 温度值 |
| `llmModelId` | Long | 否 | - | 大语言模型 ID |
| `visionModelId` | Long | 否 | - | 视觉模型 ID |
| `audioModelId` | Long | 否 | - | 语音模型 ID |
| `embeddingModelId` | Long | 否 | - | 嵌入模型 ID |
| `knowledgeBaseId` | Long | 否 | - | 知识库 ID |
| `knowledgeBaseFolderId` | Long | 否 | - | 知识库文件夹 ID |
| `systemPrompt` | String | 是 | 长度 ≤ 10000 | 系统提示词 |
| `greetingMessage` | String | 否 | 长度 ≤ 1000 | 开场白 |
| `skills` | Map\<String, Object\> | 是 | 不能为空 | 技能配置 |
| `mcpServersConfig` | List\<Map\<String, Object\>\> | 是 | 不能为空 | MCP 服务器配置 |

**请求示例：**

```json
{
  "name": "山东省政策咨询助手",
  "description": "以旧换新政策咨询",
  "modelProvider": "openai",
  "apiUrl": "https://api.example.com/v1",
  "modelName": "gpt-4o",
  "temperature": 0.3,
  "llmModelId": 1,
  "visionModelId": 2,
  "embeddingModelId": 3,
  "knowledgeBaseFolderId": 1,
  "systemPrompt": "你是山东省智能政策咨询助手...",
  "greetingMessage": "您好！请问有什么可以帮您？",
  "skills": {
    "subsidyCalculator": { "enabled": true }
  },
  "mcpServersConfig": []
}
```

### 响应

**状态码**：`200 OK`

**响应体**：`AgentConfigResponse`（结构同 [获取配置](#1-获取当前配置)，返回更新后的配置）

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 参数校验失败 |
| 401 | 未认证 |
| 403 | 非管理员 |

---

## 3. 重置为默认配置

将智能体配置重置为系统默认值，并同步到运行时。

### 请求

```
POST /api/admin/agent-config/reset
Authorization: Bearer {token}
```

无请求体。

### 响应

**状态码**：`200 OK`

**响应体**：`AgentConfigResponse`（重置后的默认配置）

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 401 | 未认证 |
| 403 | 非管理员 |

---

## 4. 测试配置

使用与正式聊天相同的 `ChatService` 发送测试消息，验证当前运行时配置是否正常工作。

### 请求

```
POST /api/admin/agent-config/test
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`AgentConfigTestRequest`）：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `message` | String | 是 | 测试消息内容（不能为空） |
| `sessionId` | String | 否 | 会话 ID，不填则使用临时会话 `admin-test-{uuid}` |

**请求示例：**

```json
{
  "message": "你好，请介绍一下你自己"
}
```

### 响应

**状态码**：`200 OK`

**响应体**：`ChatResponse`（与 [对话模块](./chat.md#1-标准对话) 的标准对话响应结构一致）

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 测试消息为空 |
| 401 | 未认证 |
| 403 | 非管理员 |
| 500 | 模型调用失败（如 API Key 无效、API 地址不通等） |
