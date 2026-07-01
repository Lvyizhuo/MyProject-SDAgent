# 模型管理模块

> 管理员对模型提供商（LLM / 视觉 / 语音 / 嵌入 / 重排序）的增删改查与连接测试。

- **路径前缀**：`/api/admin/models`
- **认证**：管理员（`ADMIN` 角色）
- **控制器**：`ModelProviderController`

## 接口一览

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/admin/models` | 获取模型列表（支持按类型过滤） |
| GET | `/api/admin/models/{id}` | 获取模型详情 |
| POST | `/api/admin/models` | 新增模型 |
| PUT | `/api/admin/models/{id}` | 更新模型 |
| DELETE | `/api/admin/models/{id}` | 删除模型 |
| PUT | `/api/admin/models/{id}/set-default` | 设为默认模型 |
| POST | `/api/admin/models/{id}/test` | 测试模型连接 |
| GET | `/api/admin/models/options` | 获取模型下拉选项 |

---

## 通用响应结构

`ModelProviderResponse` 字段：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | ID |
| `name` | String | 模型名称 |
| `type` | ModelType | 模型类型（`LLM` / `VISION` / `AUDIO` / `EMBEDDING` / `RERANK`） |
| `provider` | String | 服务商 |
| `apiUrl` | String | API 地址 |
| `modelName` | String | 具体模型名称 |
| `builtinCode` | String | 内置模型编码 |
| `dimensions` | Integer | 向量维度（嵌入模型） |
| `temperature` | BigDecimal | 温度参数 |
| `maxTokens` | Integer | 最大 Token 数 |
| `topP` | BigDecimal | TopP 采样参数 |
| `isDefault` | Boolean | 是否默认 |
| `isEnabled` | Boolean | 是否启用 |
| `builtIn` | Boolean | 是否内置模型 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |

---

## 1. 获取模型列表

### 请求

```
GET /api/admin/models?type={type}
Authorization: Bearer {token}
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `type` | ModelType | 否 | 按类型过滤：`LLM` / `VISION` / `AUDIO` / `EMBEDDING` / `RERANK` |

### 响应

**状态码**：`200 OK`

**响应体**：`List<ModelProviderResponse>`

---

## 2. 获取模型详情

### 请求

```
GET /api/admin/models/{id}
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 模型 ID |

### 响应

**状态码**：`200 OK`

**响应体**：`ModelProviderResponse`

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 模型不存在 |

---

## 3. 新增模型

### 请求

```
POST /api/admin/models
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**（`ModelProviderRequest`）：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `name` | String | 否 | 长度 ≤ 100 | 模型名称 |
| `type` | ModelType | 是 | 不能为空 | 模型类型 |
| `provider` | String | 是 | 长度 ≤ 50 | 服务商标识 |
| `apiUrl` | String | 是 | 长度 ≤ 500 | API 地址 |
| `apiKey` | String | 否 | - | API 密钥 |
| `modelName` | String | 是 | 长度 ≤ 100 | 具体模型名称 |
| `temperature` | BigDecimal | 否 | 0.0 ~ 2.0 | 温度参数 |
| `maxTokens` | Integer | 否 | 1 ~ 1000000 | 最大 Token 数 |
| `topP` | BigDecimal | 否 | 0.0 ~ 1.0 | TopP 采样参数 |
| `isDefault` | Boolean | 否 | - | 是否设为默认 |
| `isEnabled` | Boolean | 否 | - | 是否启用 |

**请求示例：**

```json
{
  "name": "通义千问",
  "type": "LLM",
  "provider": "dashscope",
  "apiUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "apiKey": "sk-xxxx",
  "modelName": "qwen-plus",
  "temperature": 0.3,
  "maxTokens": 4096,
  "topP": 0.8,
  "isDefault": false,
  "isEnabled": true
}
```

### 响应

**状态码**：`200 OK`

**响应体**：`ModelProviderResponse`（新建的模型）

---

## 4. 更新模型

### 请求

```
PUT /api/admin/models/{id}
Authorization: Bearer {token}
Content-Type: application/json
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 模型 ID |

**请求体**：`ModelProviderRequest`（同 [新增模型](#3-新增模型)）

> 💡 编辑时 `apiKey` 允许留空，留空则保持原值不变。

### 响应

**状态码**：`200 OK`

**响应体**：`ModelProviderResponse`（更新后的模型）

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 400 | 模型不存在；参数校验失败 |

---

## 5. 删除模型

### 请求

```
DELETE /api/admin/models/{id}
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 模型 ID |

### 响应

**状态码**：`204 No Content`，无响应体。

---

## 6. 设为默认模型

将指定模型设为其类型下的默认模型。

### 请求

```
PUT /api/admin/models/{id}/set-default
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 模型 ID |

### 响应

**状态码**：`200 OK`

**响应体**：`ModelProviderResponse`（更新后的模型，`isDefault=true`）

---

## 7. 测试模型连接

向模型 API 发起测试请求，验证连通性与密钥是否有效。

### 请求

```
POST /api/admin/models/{id}/test
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 模型 ID |

### 响应

**状态码**：`200 OK`

**响应体**（`Map<String, Object>`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `success` | Boolean | 是否连接成功 |
| `message` | String | 提示信息 |
| 其他 | - | 视实现而定（如延迟、返回内容等） |

**响应示例：**

```json
{
  "success": true,
  "message": "连接成功"
}
```

---

## 8. 获取模型下拉选项

返回按类型分组的模型选项，用于前端下拉选择。

### 请求

```
GET /api/admin/models/options
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

**响应体**（`Map<String, List<ModelOption>>`）：键为模型类型，值为选项列表。

**响应示例：**

```json
{
  "LLM": [
    { "id": 1, "name": "通义千问", "provider": "dashscope" }
  ],
  "EMBEDDING": [
    { "id": 3, "name": "nomic-embed-text", "provider": "ollama" }
  ]
}
```
