# 对话模块

> 智能政策咨询助手的核心对话能力，支持标准对话与流式对话（SSE）。

- **路径前缀**：`/api/chat`
- **认证**：可选（携带 JWT 时记录用户身份，匿名也可对话）
- **控制器**：`ChatController`

## 1. 标准对话

返回完整的 AI 回复（含 RAG 引用来源）。

### 请求

```
POST /api/chat
Content-Type: application/json
Authorization: Bearer {token}   # 可选
```

**请求体**（`ChatRequest`）：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `message` | String | 是 | 用户消息（不能为空） |
| `conversationId` | String | 否 | 会话 ID，用于多轮对话上下文；为空时自动生成 UUID |
| `cityCode` | String | 否 | 城市代码，用于过滤地市政策 |
| `latitude` | Double | 否 | 浏览器定位纬度 |
| `longitude` | Double | 否 | 浏览器定位经度 |
| `locationAccuracy` | Double | 否 | 浏览器定位精度（米） |
| `imageBase64List` | List\<String\> | 否 | 图片 Base64 数据列表，最多 3 张，每张 ≤ 5MB。**纯 base64 字符串**（不含 `data:image/...;base64,` 前缀） |
| `imageFormat` | String | 否 | 图片格式，默认 `jpeg`，支持 `jpeg` / `jpg` / `png` / `webp` |

**请求示例：**

```json
{
  "message": "山东省家电以旧换新补贴标准是多少？",
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "cityCode": "370100"
}
```

**多模态请求示例（带图片）：**

```json
{
  "message": "帮我看看这台空调能补贴多少",
  "imageBase64List": ["/9j/4AAQSkZJRg..."],
  "imageFormat": "jpeg"
}
```

### 响应

**状态码**：`200 OK`

**响应体**（`ChatResponse`）：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | String | 响应 ID |
| `conversationId` | String | 会话 ID |
| `content` | String | AI 回复内容 |
| `references` | List\<Reference\> | 引用来源列表（RAG 检索结果） |
| `timestamp` | LocalDateTime | 响应时间戳 |

**Reference（引用来源）字段：**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `documentId` | Long | 文档 ID |
| `title` | String | 标题 |
| `url` | String | URL |
| `sourceSite` | String | 来源站点 |
| `publishedAt` | String | 发布时间 |
| `scope` | String | 适用范围 |
| `keywords` | List\<String\> | 关键词 |
| `snippet` | String | 摘要片段 |
| `documentName` | String | 文档名称 |
| `content` | String | 内容 |
| `pageNumber` | Integer | 页码 |
| `score` | Double | 相关度评分 |

**响应示例：**

```json
{
  "id": "a1b2c3d4-...",
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "content": "根据山东省以旧换新政策，家电类产品补贴标准为...",
  "references": [
    {
      "documentId": 12,
      "title": "山东省推动消费品以旧换新实施方案",
      "sourceSite": "山东省人民政府",
      "snippet": "对家电以旧换新给予补贴...",
      "score": 0.92
    }
  ],
  "timestamp": "2025-07-01T19:54:20"
}
```

### 图片校验规则

- 最多 **3 张** 图片，超出报错
- 单张 ≤ **5MB**
- 格式限 `jpeg` / `png` / `webp`，并通过文件头（magic bytes）二次校验
- Base64 必须可解码

---

## 2. 流式对话（SSE）

以 Server-Sent Events 方式逐块推送 AI 回复。

> ⚠️ **注意**：仅当配置 `app.chat.mode=dual` 时可用；默认 `sync-only` 模式下该接口返回 `410 Gone`，提示改用 `/api/chat`。

### 请求

```
POST /api/chat/stream
Content-Type: application/json
Authorization: Bearer {token}   # 可选
Accept: text/event-stream
```

**请求体**：同 [标准对话](#1-标准对话) 的 `ChatRequest`。

### 响应

- **可用时**：`200 OK`，`Content-Type: text/event-stream`，每条事件为 JSON 序列化的 `ChatStreamEvent`
- **未开启时**：`410 Gone`，JSON：

```json
{
  "code": "STREAM_ENDPOINT_REMOVED",
  "message": "流式接口已下线，请改用 /api/chat 非流式接口"
}
```

**ChatStreamEvent 事件结构：**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `type` | String | 事件类型：`delta`（内容增量）/ `references`（引用来源）/ `error`（错误） |
| `content` | String | 内容增量（`type=delta` 时） |
| `message` | String | 错误消息（`type=error` 时） |
| `references` | List\<Reference\> | 引用来源（`type=references` 时） |

**事件流示例：**

```
data:{"type":"delta","content":"根据"}

data:{"type":"delta","content":"山东省政策"}

data:{"type":"references","references":[{"documentId":12,"title":"..."}]}
```

---

## 3. 健康检查

### 请求

```
GET /api/chat/health
```

### 响应

**状态码**：`200 OK`

**响应体**：纯文本 `OK`
