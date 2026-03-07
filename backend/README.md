# 山东省智能政策咨询助手 - Backend

基于 Spring Boot 3.4.1 + Spring AI 1.0.3 的后端服务，提供用户对话、多模态识别、管理员控制台配置管理和知识库管理能力。

## 依赖服务

- PostgreSQL 16 + pgvector（向量检索）
- Redis 7（会话/记忆）
- MinIO（知识库原始文件存储）

```bash
cd backend
docker compose up -d
```

默认端口：
- `8080` 后端 API
- `5432` PostgreSQL
- `6379` Redis
- `9000` MinIO API
- `9001` MinIO Console

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

```bash
export DASHSCOPE_API_KEY="your-api-key"
export TAVILY_API_KEY="your-tavily-key"
./mvnw spring-boot:run
```

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
- `GET /api/admin/knowledge/folders`
- `POST /api/admin/knowledge/documents`
- `GET /api/admin/knowledge/documents`
- `GET /api/admin/knowledge/documents/{id}/chunks`
- `POST /api/admin/knowledge/documents/{id}/reingest`

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
- `SecurityConfig` 对 `/api/admin/**` 启用 `ROLE_ADMIN` 鉴权。
- 知识库配置由 `app.knowledge.*` 驱动，包含 MinIO、切片、召回、重排与嵌入模型配置。
