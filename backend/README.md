# 山东省智能政策咨询助手 - Backend

基于 Spring Boot 3.4.1 + Spring AI 1.0.3 的后端服务，提供用户对话、多模态识别、多智能体管理、管理员控制台配置管理、知识库管理和模型服务管理能力。

## 依赖服务

- PostgreSQL 16 + pgvector（向量检索）
- Redis 7（会话/记忆）
- MinIO（知识库原始文件存储）
- Ollama（本地嵌入模型，默认 `bge-m3`，`docker-compose.yml` 默认包含）
- Reranker（本地重排序模型 `bge-reranker-v2-m3`，自建 `rerank-service` 容器）

```bash
cd backend
# 仅启动依赖（用于本地 ./mvnw spring-boot:run）
docker compose up -d postgres redis minio ollama ollama-init reranker

# 或：启动全部（包含 backend 容器）
docker compose up -d
```

默认端口：
- `8080` 后端 API
- `5433` PostgreSQL
- `6379` Redis
- `9000` MinIO API
- `9001` MinIO Console
- `11434` Ollama
- `8989` Reranker

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
- `APP_RAG_RERANK_PROVIDER` / `APP_RAG_RERANK_MODEL` / `APP_RAG_RERANK_ENDPOINT`（重排序配置）

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
- `APP_RAG_RERANK_PROVIDER`（可选，重排序 provider：tei/dashscope/xinference，默认 tei）
- `APP_RAG_RERANK_MODEL`（可选，重排序模型名，默认 bge-reranker-v2-m3）
- `APP_RAG_RERANK_ENDPOINT`（可选，重排序端点，默认 http://localhost:8989/rerank）
- `APP_KNOWLEDGE_EMBEDDING_DEFAULT_MODEL`（可选，默认嵌入模型，默认 ollama:bge-m3）
- `APP_KNOWLEDGE_MIGRATION_ENABLED`（可选，知识库迁移开关，默认 false）

```bash
export DASHSCOPE_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-key"
export APP_JWT_SECRET="your-base64-secret"
export APP_MODEL_PROVIDER_ENCRYPTION_SECRET="your-model-provider-secret"
./mvnw spring-boot:run
```

应用内重要配置项：
- `app.agent.planning-timeout-seconds`：ReAct 规划超时时间，当前默认 15 秒
- `app.model-provider.openai.*`：OpenAI 兼容调用超时配置（connect=10s, read=90s）
- `app.model-provider.direct.*`：直连模型测试/降级调用超时配置（connect=10s, read=90s）
- `app.rag.retrieval.rerank-max-doc-chars`：发送给 rerank 的单条文档最大字符数，默认 3000
- `app.rag.retrieval.prompt-max-doc-chars`：注入到对话提示词的单条文档最大字符数，默认 1200

## API 速览

核心用户接口：
- `POST /api/chat`
- `POST /api/chat/stream`
- `GET /api/chat/health`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

管理员接口 — 智能体配置（旧）：
- `POST /api/admin/auth/login`
- `POST /api/admin/auth/change-password`
- `GET|PUT /api/admin/agent-config`
- `POST /api/admin/agent-config/reset`
- `POST /api/admin/agent-config/test`

管理员接口 — 多智能体管理（新增）：
- `GET /api/admin/agents`
- `GET /api/admin/agents/{id}`
- `POST /api/admin/agents`
- `PUT /api/admin/agents/{id}`
- `DELETE /api/admin/agents/{id}`
- `POST /api/admin/agents/{id}/default`
- `POST /api/admin/agents/{id}/duplicate`
- `GET /api/admin/agents/_meta`

管理员接口 — 工具管理（新增）：
- `GET /api/admin/tools`
- `GET /api/admin/tools/{id}`
- `POST /api/admin/tools`
- `PUT /api/admin/tools/{id}`
- `DELETE /api/admin/tools/{id}`
- `POST /api/admin/tools/{id}/enabled`
- `POST /api/admin/tools/{id}/test`

管理员接口 — 模型卡片视图（新增）：
- `GET /api/admin/models-card`
- `GET /api/admin/models-card/{id}`
- `POST /api/admin/models-card/{id}/default`
- `POST /api/admin/models-card/{id}/test`

管理员接口 — 模型管理：
- `GET /api/admin/models`
- `GET /api/admin/models/{id}`
- `POST /api/admin/models`
- `PUT /api/admin/models/{id}`
- `DELETE /api/admin/models/{id}`
- `PUT /api/admin/models/{id}/set-default`
- `POST /api/admin/models/{id}/test`
- `GET /api/admin/models/options`

管理员接口 — 知识库：
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
- `GET /api/admin/knowledge/archive/export`
- `POST /api/admin/knowledge/archive/import`
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
- `AgentInstanceService` 管理多智能体实例（CRUD/默认设置/复制），并通过 `syncDefaultAgentToRuntime` 将默认智能体配置同步到运行时 AgentConfig。
- `ConsoleCardSeedService` 在应用启动时检查空库并自动种子化默认智能体、工具和模型数据。
- `ModelProviderService` 负责模型 CRUD、默认模型切换、连接测试、运行时解密，以及内置模型与托管模型的自动合并。
- `ModelCardService` 提供轻量级卡片视图，只读 + 简化测试，不修改底层 ModelProvider 数据。
- `ToolDefinitionService` 管理工具定义的 CRUD 和全局启用/停用控制。
- `RuntimeRagVectorStore` 会优先使用管理员绑定的嵌入模型做向量检索，未绑定时回退到知识库默认嵌入模型。
- `DashScopeRerankService` 支持 TEI/Xinference/DashScope 三种重排序 provider，含端点自动补全和 404 自动重试回退。
- `DocumentLoaderService` 在扫描版 PDF 无法直接提取文本时，会调用视觉能力做 OCR 兜底；若仍无文本则阻止入库并标记失败。
- `ChatService` 会对实时查询优先执行 `webSearch`，并在 OpenAI 兼容调用 404 / 网络超时等异常场景下尝试原生 REST 降级。
- `BusinessException` 统一业务异常，由 `GlobalExceptionHandler` 拦截并按 HttpStatus 动态设置响应码。
- Flyway V9 新增 `agent_instance`、`agent_model_binding`、`tool_definition` 三张表（Hibernate ddl-auto: update 自动建表，SQL 脚本作为 schema 文档保留）。
- Lombok 升级至 1.18.46，`lombok.config` 关闭 fluent/chain 模式，Boolean 字段使用显式 getter/setter 绕开命名歧义。
- Maven 构建使用 `maven-settings.xml` 阿里云镜像加速，Docker 构建使用 `--no-cache` 避免 Lombok 旧 class 缓存。
