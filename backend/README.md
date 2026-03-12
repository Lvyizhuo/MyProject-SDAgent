# 山东省智能政策咨询助手 - Backend

基于 Spring Boot 3.4.1 + Spring AI 1.0.3 的后端服务，提供用户对话、多模态识别、管理员控制台配置管理、知识库管理和模型服务管理能力。

## 依赖服务

- PostgreSQL 16 + pgvector（向量检索）
- Redis 7（会话/记忆）
- MinIO（知识库原始文件存储）
- Ollama（本地嵌入模型服务，可选但 `docker-compose.yml` 默认包含）

```bash
cd backend
# 仅启动依赖（用于本地 ./mvnw spring-boot:run）
docker compose up -d postgres redis minio ollama ollama-init

# 或：启动全部（包含 backend 容器）
docker compose up -d
```

默认端口：
- `8080` 后端 API
- `5432` PostgreSQL
- `6379` Redis
- `9000` MinIO API
- `9001` MinIO Console
- `11434` Ollama

## 服务器部署（通过项目根目录 deploy）

生产环境建议使用项目根目录 `deploy/docker-compose.yml` 一次启动前后端与依赖服务：

```bash
cd /www/wwwroot/MyProject-SDAgent
cp deploy/.env.example deploy/.env
vi deploy/.env

cd deploy
docker compose --env-file .env up -d --build
docker compose ps
```

关键环境变量（生产）：
- `DASHSCOPE_API_KEY`
- `APP_JWT_SECRET`
- `APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS`
- `APP_EMBEDDING_OLLAMA_BASE_URL`
- `APP_MODEL_PROVIDER_ENCRYPTION_SECRET`（启用模型管理时建议显式配置）

## 运行与测试

```bash
cd backend

# 构建
./mvnw clean package

# 运行
./mvnw spring-boot:run

# 若使用 .env 文件，推荐先导出环境变量再运行
set -a; source .env; set +a
./mvnw spring-boot:run

# mcp profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp

# 测试
./mvnw test
./mvnw test -Dtest=ChatServiceTest
```

## 环境变量

- `DASHSCOPE_API_KEY`（必需）
- `TAVILY_API_KEY`（可选，联网搜索）
- `APP_JWT_SECRET`（生产必需）
- `APP_MODEL_PROVIDER_ENCRYPTION_SECRET`（可选但推荐，用于模型 API Key 加密）
- `APP_MODEL_PROVIDER_LEGACY_ENCRYPTION_SECRETS`（可选，用于历史密钥兼容解密）

```bash
export DASHSCOPE_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-key"
export APP_JWT_SECRET="your-base64-secret"
export APP_MODEL_PROVIDER_ENCRYPTION_SECRET="your-model-provider-secret"
./mvnw spring-boot:run
```

应用内重要配置项：
- `app.agent.planning-timeout-seconds`：ReAct 规划超时时间，当前默认 15 秒
- `app.model-provider.openai.*`：OpenAI 兼容调用超时配置
- `app.model-provider.direct.*`：直连模型测试/降级调用超时配置

## API 速览

核心用户接口：
- `POST /api/chat`
- `POST /api/chat/stream`
- `GET /api/chat/health`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

管理员接口：
- `POST /api/admin/auth/login`
- `POST /api/admin/auth/change-password`
- `GET|PUT /api/admin/agent-config`
- `POST /api/admin/agent-config/reset`
- `POST /api/admin/agent-config/test`
- `GET /api/admin/models`
- `GET /api/admin/models/{id}`
- `POST /api/admin/models`
- `PUT /api/admin/models/{id}`
- `DELETE /api/admin/models/{id}`
- `PUT /api/admin/models/{id}/set-default`
- `POST /api/admin/models/{id}/test`
- `GET /api/admin/models/options`
- `GET /api/admin/knowledge/folders`
- `POST /api/admin/knowledge/folders`
- `PUT /api/admin/knowledge/folders/{id}`
- `DELETE /api/admin/knowledge/folders/{id}`
- `POST /api/admin/knowledge/documents`
- `POST /api/admin/knowledge/documents/extract-metadata`
- `GET /api/admin/knowledge/documents`
- `GET /api/admin/knowledge/documents/selection`
- `GET /api/admin/knowledge/documents/{id}`
- `GET /api/admin/knowledge/documents/{id}/chunks`
- `GET /api/admin/knowledge/documents/{id}/download`
- `GET /api/admin/knowledge/documents/{id}/preview`
- `DELETE /api/admin/knowledge/documents/{id}`
- `POST /api/admin/knowledge/documents/{id}/reingest`
- `POST /api/admin/knowledge/documents/batch-delete`
- `POST /api/admin/knowledge/documents/batch-move`
- `GET /api/admin/knowledge/embedding-models`
- `GET|PUT /api/admin/knowledge/config`
- `POST /api/admin/knowledge/url-imports`
- `GET /api/admin/knowledge/url-imports`
- `GET /api/admin/knowledge/url-imports/{id}`
- `POST /api/admin/knowledge/url-imports/{id}/confirm`
- `POST /api/admin/knowledge/url-imports/batch-confirm`
- `POST /api/admin/knowledge/url-imports/{id}/reject`
- `POST /api/admin/knowledge/url-imports/{id}/cancel`
- `DELETE /api/admin/knowledge/url-imports/{id}`
- `DELETE /api/admin/knowledge/url-import-items/{id}`

公开配置接口：
- `GET /api/public/config/agent`

多模态接口：
- `POST /api/multimodal/transcribe`
- `POST /api/multimodal/analyze-image`
- `POST /api/multimodal/analyze-invoice`
- `POST /api/multimodal/analyze-device`

## 健康检查

```bash
curl http://localhost:8080/api/chat/health
curl http://localhost:8080/actuator/health
```

## 关键实现说明

- 默认管理员账号会在首次启动时初始化为 `admin/admin`（`AdminInitializer`），上线前必须修改。
- `SecurityConfig` 对 `/api/admin/**` 启用 `ROLE_ADMIN` 鉴权，并放行 `/api/public/config/**` 供前端读取开场白等公开信息。
- `DynamicChatClientFactory` 会基于 `AgentConfig` 中选定的 LLM 动态创建当前请求的 ChatClient。
- `RuntimeRagVectorStore` 会优先使用管理员绑定的嵌入模型做向量检索，未绑定时回退到知识库默认嵌入模型。
- `ModelProviderService` 负责模型 CRUD、默认模型切换、连接测试，以及模型 API Key 的 AES-GCM 加解密。
- `DocumentLoaderService` 在扫描版 PDF 无法直接提取文本时，会调用视觉能力做 OCR 兜底；若仍无文本则阻止入库并标记失败。
- `ChatService` 会对实时查询优先执行 `webSearch`，并在 OpenAI 兼容调用 404 / 网络超时等异常场景下尝试原生 REST 降级。
