# 会话管理模块

> 管理用户的聊天会话历史记录（基于 Redis 存储）。

- **路径前缀**：`/api/conversations`
- **认证**：需要（必须登录）
- **控制器**：`ConversationController`

## 接口一览

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/conversations` | 获取当前用户的会话列表 |
| GET | `/api/conversations/{sessionId}` | 获取/创建指定会话 |
| DELETE | `/api/conversations/{sessionId}` | 删除指定会话 |

---

## 1. 获取会话列表

返回当前登录用户的所有会话。

### 请求

```
GET /api/conversations
Authorization: Bearer {token}
```

### 响应

**状态码**：`200 OK`

**响应体**：`List<ChatSession>`

**ChatSession 字段：**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | String | 会话 ID |
| `title` | String | 会话标题 |
| `userId` | Long | 用户 ID |
| `messages` | List\<ChatMessage\> | 消息列表 |
| `createdAt` | Long | 创建时间戳（毫秒） |
| `updatedAt` | Long | 更新时间戳（毫秒） |

**ChatMessage 字段：**

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | String | 消息 ID |
| `role` | String | 角色（`user` / `assistant`） |
| `content` | String | 消息内容 |
| `references` | List\<Reference\> | 引用来源（仅 assistant 消息） |
| `timestamp` | Long | 时间戳（毫秒） |

**响应示例：**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "家电以旧换新咨询",
    "userId": 1,
    "messages": [
      {
        "id": "msg-1",
        "role": "user",
        "content": "补贴标准是多少？",
        "timestamp": 1751358860000
      }
    ],
    "createdAt": 1751358860000,
    "updatedAt": 1751358870000
  }
]
```

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 401 | 未认证 |

---

## 2. 获取/创建指定会话

若会话已存在则返回其内容；若不存在则创建一个新会话。

### 请求

```
GET /api/conversations/{sessionId}
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `sessionId` | String | 会话 ID |

### 响应

**状态码**：`200 OK`

**响应体**：`ChatSession`（结构同上）

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 401 | 未认证 |

---

## 3. 删除会话

删除当前用户指定的会话及其全部消息记录。

### 请求

```
DELETE /api/conversations/{sessionId}
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 说明 |
| :--- | :--- | :--- |
| `sessionId` | String | 会话 ID |

### 响应

**状态码**：`200 OK`，无响应体。

### 错误

| 状态码 | 场景 |
| :--- | :--- |
| 401 | 未认证 |
| 400 | 会话不属于当前用户或不存在 |
