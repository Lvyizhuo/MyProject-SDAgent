# 山东省智能政策咨询助手 — 接口文档

> 基于 AI/LLM 的山东省以旧换新政策咨询助手后端 REST API 文档。

## 目录

| 模块 | 文档 | 路径前缀 | 认证 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| 对话 | [chat.md](./chat.md) | `/api/chat` | 可选 | 标准对话、流式对话、健康检查 |
| 用户认证 | [auth.md](./auth.md) | `/api/auth` | 部分 | 注册、登录、获取当前用户 |
| 会话管理 | [conversations.md](./conversations.md) | `/api/conversations` | 需要 | 会话列表、获取/创建、删除 |
| 文档管理 | [documents.md](./documents.md) | `/api/documents` | 无 | 默认文档加载、目录加载、删除向量 |
| 多模态 | [multimodal.md](./multimodal.md) | `/api/multimodal` | 无 | 语音转写、图像/发票/设备识别 |
| 公开配置 | [public-config.md](./public-config.md) | `/api/public/config` | 无 | 获取智能体公开配置（开场白） |
| 管理员认证 | [admin-auth.md](./admin-auth.md) | `/api/admin/auth` | 部分 | 管理员登录、修改密码 |
| 智能体配置 | [admin-agent-config.md](./admin-agent-config.md) | `/api/admin/agent-config` | 管理员 | 获取/更新/重置/测试智能体配置 |
| 模型管理 | [admin-models.md](./admin-models.md) | `/api/admin/models` | 管理员 | 模型提供商 CRUD、设默认、测试连接 |
| 知识库管理 | [admin-knowledge.md](./admin-knowledge.md) | `/api/admin/knowledge` | 管理员 | 文件夹、文档、分块、配置、网站导入、归档 |

---

## 通用约定

### 基础信息

- **Base URL**：`http://{host}:8080`
- **内容类型**：`application/json`（文件上传为 `multipart/form-data`）
- **字符编码**：UTF-8
- **时间格式**：`LocalDateTime` / `LocalDate`（ISO-8601，如 `2025-07-01T19:54:20`）

### 认证机制

系统采用 **JWT（JSON Web Token）** 无状态认证。

1. 通过 `/api/auth/login`（用户）或 `/api/admin/auth/login`（管理员）获取 `token`。
2. 后续请求在请求头携带：`Authorization: Bearer {token}`。
3. 服务端无 Session，每次请求独立校验。

**认证级别说明：**

| 级别 | 含义 | 触发条件 |
| :--- | :--- | :--- |
| 无 | 无需认证 | 公开接口（登录、注册、公开配置、对话、文档、多模态等） |
| 可选 | 可携带，也可匿名 | `/api/chat/**`：携带时记录用户身份，匿名也可用 |
| 需要 | 必须登录 | `/api/conversations/**`、`/api/auth/me` |
| 管理员 | 必须 ADMIN 角色 | `/api/admin/**`（管理员登录除外） |

### 通用错误响应

所有接口在异常时返回统一结构（由 `GlobalExceptionHandler` 处理）：

```json
{
  "timestamp": "2025-07-01T19:54:20.123",
  "status": 400,
  "message": "错误描述"
}
```

**常见错误状态码：**

| HTTP 状态码 | 场景 |
| :--- | :--- |
| `400 Bad Request` | 参数校验失败、非法参数、业务异常（`IllegalArgumentException`） |
| `401 Unauthorized` | 未认证、用户名或密码错误、用户不存在 |
| `403 Forbidden` | 权限不足（非管理员访问管理接口） |
| `404 Not Found` | 资源不存在 |
| `413 Payload Too Large` | 上传文件超过 30MB |
| `500 Internal Server Error` | 系统内部异常 |

**参数校验错误（400）** 响应结构：

```json
{
  "timestamp": "2025-07-01T19:54:20.123",
  "status": 400,
  "errors": {
    "username": "用户名不能为空",
    "password": "密码长度必须在6-100之间"
  }
}
```

**认证失败（401）** 响应：

```json
{
  "status": 401,
  "message": "未认证，请先登录",
  "timestamp": "2025-07-01T19:54:20.123"
}
```

**权限不足（403）** 响应：

```json
{
  "status": 403,
  "message": "权限不足，无法访问",
  "timestamp": "2025-07-01T19:54:20.123"
}
```

### 分页约定

分页接口统一使用以下查询参数：

| 参数 | 类型 | 默认值 | 说明 |
| :--- | :--- | :--- | :--- |
| `page` | int | `0` | 页码（从 0 开始） |
| `size` | int | `20` | 每页条数 |
| `sortBy` | String | `createdAt` | 排序字段 |
| `sortDir` | String | `DESC` | 排序方向（`ASC` / `DESC`） |

分页响应结构：

```json
{
  "content": [ /* 当前页数据 */ ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

### 枚举值速查

| 枚举 | 取值 |
| :--- | :--- |
| `Role`（角色） | `USER`、`ADMIN` |
| `ModelType`（模型类型） | `LLM`、`VISION`、`AUDIO`、`EMBEDDING`、`RERANK` |
| `DocumentStatus`（文档状态） | `PENDING`、`PROCESSING`、`COMPLETED`、`FAILED` |
| `KnowledgeBaseInitStatus`（知识库初始化状态） | `INITIALIZING`、`READY`、`FAILED` |
| `UrlImportJobStatus`（网站导入任务状态） | `PENDING`、`CRAWLING`、`PROCESSING`、`WAITING_CONFIRM`、`PARTIALLY_IMPORTED`、`COMPLETED`、`CANCELED`、`FAILED` |
| `UrlImportItemType`（导入条目类型） | `ARTICLE`、`ATTACHMENT` |
| `UrlImportParseStatus`（解析状态） | `PENDING`、`PARSED`、`FILTERED`、`FAILED` |
| `UrlImportReviewStatus`（审核状态） | `WAITING_CONFIRM`、`CONFIRMED`、`REJECTED`、`IMPORT_FAILED` |

### CORS 跨域

默认允许来源：`http://localhost:*`、`http://127.0.0.1:*`、`http://192.168.*.*:*`、`http://10.*.*.*:*`、`http://172.16~31.*.*:*`，可通过配置项 `app.security.cors.allowed-origin-patterns` 覆盖。允许方法：GET / POST / PUT / DELETE / OPTIONS。

---

## 端口与路由总览

### 1. 端口清单

| 端口 | 服务 | 协议 | 绑定地址 | 用途 |
| :--- | :--- | :--- | :--- | :--- |
| `80` | Nginx（宝塔反代） | HTTP | `0.0.0.0` | 生产统一入口，对外暴露 |
| `8080` | Backend（Spring Boot） | HTTP | `127.0.0.1` | REST API + Actuator 监控 |
| `5173` | Frontend | HTTP | `127.0.0.1` | 前端页面（开发=Vite / 生产=容器内 Nginx 映射） |
| `5432` | PostgreSQL（pgvector） | TCP | `127.0.0.1` | 主数据库 + 向量存储 |
| `6379` | Redis | TCP | `127.0.0.1` | 聊天记忆 / 会话存储 / 缓存 |
| `9000` | MinIO API | HTTP | `127.0.0.1` | 知识库原始文档对象存储 |
| `9001` | MinIO Console | HTTP | `127.0.0.1` | MinIO 管理控制台 |
| `11434` | Ollama | HTTP | `127.0.0.1` | 本地嵌入模型（bge-m3 / nomic-embed-text） |
| `9997` | Xinference | HTTP | `127.0.0.1` | 本地重排序模型（bge-reranker-v2-m3） |

> 🔒 除 `80` 端口（Nginx）对外暴露外，其余端口均绑定 `127.0.0.1`，仅本机访问，由 Nginx 统一转发。

**外部依赖服务（HTTPS 443）：**

| 服务 | 地址 | 用途 |
| :--- | :--- | :--- |
| DashScope | `https://dashscope.aliyuncs.com` | 通义千问 LLM / 视觉 / 语音 / 嵌入 |
| Tavily | `https://api.tavily.com` | 联网搜索 |

---

### 2. 生产环境路由（Nginx 反向代理，端口 80）

配置文件：`deploy/bt-site-mmgg.dpdns.org.conf`，`server_name mmgg.dpdns.org`。

| 路径 | 转发目标 | 关键配置 | 说明 |
| :--- | :--- | :--- | :--- |
| `/` | `http://127.0.0.1:5173` | 标准 proxy 头 | 前端静态页面 |
| `/api/` | `http://127.0.0.1:8080/api/` | `proxy_buffering off`、`proxy_cache off`、`read/send_timeout 600s` | 后端 API（关闭缓冲以支持 SSE 流式对话） |
| `/actuator/` | `http://127.0.0.1:8080/actuator/` | 标准 proxy 头 | 健康检查 / 监控端点 |

> 请求头统一透传：`Host`、`X-Real-IP`、`X-Forwarded-For`、`X-Forwarded-Proto`。`client_max_body_size 50m`（支持文档上传）。

---

### 3. 开发环境代理（Vite Dev Server）

配置文件：`frontend/vite.config.js`，开发服务器监听 `5173`。

| 匹配路径 | 转发目标 | 配置 | 说明 |
| :--- | :--- | :--- | :--- |
| `/api` | `env.VITE_DEV_PROXY_TARGET` 或 `http://localhost:8080` | `changeOrigin: true` | 前端开发时 API 请求代理到本地后端 |

> 开发模式下浏览器访问 `http://localhost:5173`，前端发起的 `/api/**` 请求由 Vite 转发到 `localhost:8080`，无需处理跨域。

---

### 4. Docker 容器端口映射

配置文件：`backend/docker-compose.yml`（开发）与 `deploy/docker-compose.yml`（生产）。

| 容器服务 | 容器内端口 | 宿主机端口 | 绑定 | 说明 |
| :--- | :--- | :--- | :--- | :--- |
| `postgres` | 5432 | 5432 | `127.0.0.1` | pgvector/pg16 |
| `redis` | 6379 | 6379 | `127.0.0.1` | redis:7-alpine |
| `minio` | 9000 | 9000 | `127.0.0.1` | S3 API |
| `minio` | 9001 | 9001 | `127.0.0.1` | 管理控制台 |
| `ollama` | 11434 | 11434 | `127.0.0.1` | 嵌入模型服务 |
| `xinference` | 9997 | 9997 | `127.0.0.1` | 重排序模型服务 |
| `backend` | 8080 | 8080 | `127.0.0.1` | Spring Boot |
| `frontend`（仅生产） | 80 | 5173 | `127.0.0.1` | 容器内 Nginx 80 → 宿主机 5173 |

> `ollama-init`、`xinference-init` 为一次性初始化容器，不暴露端口。

---

### 5. 容器内服务互联（Docker 网络）

后端容器通过 Docker 内部 DNS 访问依赖服务（不走宿主机端口）：

| 调用方 | 被调用方（容器名:端口） | 用途 |
| :--- | :--- | :--- |
| `backend` | `postgres:5432` | 数据库读写 |
| `backend` | `redis:6379` | 会话记忆 / 缓存 |
| `backend` | `minio:9000` | 文档上传 / 下载 / 预签名 |
| `backend` | `ollama:11434` | 嵌入向量生成 |
| `backend` | `xinference:9997` | 检索结果重排序 |
| `ollama-init` | `ollama:11434` | 拉取 bge-m3 / nomic-embed-text 模型 |
| `xinference-init` | `xinference:9997` | 启动 bge-reranker-v2-m3 模型 |
| `frontend`（生产） | 经宿主机 `127.0.0.1:8080` | API 请求（通过 Nginx 反代） |

---

### 6. 数据流向图

```
                        ┌─────────────────────────────────────┐
                        │          用户浏览器                  │
                        └───────────────┬─────────────────────┘
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 │                                             │
          【生产】Nginx :80                            【开发】Vite :5173
          server_name mmgg.dpdns.org                    host: true
                 │                                             │
      ┌──────────┼──────────────┐                              │ /api → localhost:8080
      │          │              │                              │ (changeOrigin)
      ▼          ▼              ▼                              ▼
  /         /api/        /actuator/                     前端页面 + HMR
      │          │              │
      │          │              │
      ▼          ▼              ▼
  127.0.0.1   127.0.0.1     127.0.0.1
   :5173       :8080          :8080
  前端页面   后端 API       Actuator
                │
                │  Spring Boot (Spring AI + RAG)
                │
   ┌────────────┼────────────────────────────────────────────┐
   │            │                                            │
   ▼            ▼                                            ▼
PostgreSQL   Redis        MinIO       Ollama      Xinference   外部 HTTPS:443
 :5432       :6379        :9000       :11434       :9997       DashScope/Tavily
主库+向量    会话记忆      文档存储    嵌入向量      重排序      LLM/视觉/语音/搜索
pgvector     TTL 7d        S3 API     bge-m3       bge-reranker
                                                v2-m3
```

---

### 7. 健康检查端点

| 服务 | 检查方式 | 路径 / 命令 |
| :--- | :--- | :--- |
| Backend | HTTP | `GET /actuator/health`（`show-details: always`） |
| Backend（业务） | HTTP | `GET /api/chat/health` |
| Frontend（生产容器） | HTTP | `GET /health` |
| PostgreSQL | Shell | `pg_isready -U postgres` |
| Redis | Shell | `redis-cli ping` |
| MinIO | HTTP | `GET /minio/health/live` |
| Ollama | Shell | `ollama list` |
| Xinference | HTTP | `GET /v1/models` |

**常用健康检查命令（服务器）：**

```bash
curl -f http://127.0.0.1:8080/actuator/health   # 后端
curl -f http://127.0.0.1:8080/api/chat/health   # 业务健康
curl -f http://127.0.0.1:5173/health            # 前端（生产）
```
