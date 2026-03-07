# 山东省智能政策咨询助手

基于 AI/LLM 的山东省以旧换新政策咨询系统，支持政策问答、补贴计算、多模态识别，以及管理员控制台知识库管理。

## 技术架构

| 层级 | 技术栈 |
|------|--------|
| 前端 | React 19 + Vite 7 |
| 后端 | Spring Boot 3.4.1 + Spring AI 1.0.3 |
| 大模型 | 阿里云 DashScope（聊天默认 `qwen3.5-plus`） |
| 向量数据库 | PostgreSQL 16 + pgvector |
| 缓存/会话 | Redis 7 |
| 对象存储 | MinIO |
| 鉴权 | Spring Security + JWT |

## 项目结构

```text
├── backend/
│   ├── src/main/java/com/shandong/policyagent/
│   │   ├── advisor/       # 安全、记忆、日志、重读校验
│   │   ├── agent/         # ToolIntentClassifier / AgentPlanParser
│   │   ├── config/        # ChatClient/Security/Embedding/Minio 配置
│   │   ├── controller/    # Chat/Auth/Admin/Knowledge API
│   │   ├── entity/        # JPA 实体
│   │   ├── multimodal/    # ASR 与视觉
│   │   ├── rag/           # 知识库切片、检索、向量写入
│   │   ├── service/       # 业务逻辑
│   │   └── tool/          # calculateSubsidy / parseFile / webSearch
│   ├── src/main/resources/
│   ├── docker-compose.yml # PostgreSQL + Redis + MinIO
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── pages/         # 含 AdminConsolePage
│   │   ├── components/    # 含 admin/ 子模块
│   │   └── services/      # api / adminApi / adminKnowledgeApi
│   └── package.json
├── data/
└── docs/
```

## 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- Docker & Docker Compose

### 1. 设置环境变量

```bash
export DASHSCOPE_API_KEY=your_dashscope_api_key
export TAVILY_API_KEY=your_tavily_api_key   # 可选，联网搜索工具
```

### 2. 启动基础设施

```bash
cd backend
docker compose up -d
```

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run

# mcp profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=mcp
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

## 服务器部署（宝塔 + Docker Compose）

推荐在 Linux 服务器使用 `deploy/` 目录一键部署（包含前后端 + PostgreSQL + Redis + MinIO + Ollama）。

### 1. 准备部署配置

```bash
cd /www/wwwroot/MyProject-SDAgent
cp deploy/.env.example deploy/.env
vi deploy/.env
```

至少需要正确配置：
- `DASHSCOPE_API_KEY`
- `APP_JWT_SECRET`
- `POSTGRES_PASSWORD`
- `MINIO_PASSWORD`
- `APP_SECURITY_CORS_ALLOWED_ORIGIN_PATTERNS`
- `APP_EMBEDDING_OLLAMA_BASE_URL`（容器内默认 `http://ollama:11434`）

### 2. 启动容器

```bash
cd /www/wwwroot/MyProject-SDAgent/deploy
docker compose --env-file .env up -d --build
docker compose ps
```

### 3. 健康检查

```bash
curl -f http://127.0.0.1:8080/actuator/health
curl -f http://127.0.0.1:8080/api/chat/health
curl -f http://127.0.0.1:5173/health
```

### 4. 宝塔 Nginx 反向代理要点

- 在宝塔“自定义配置”中不要包 `server {}`。
- 同一个站点只保留一个 `location /`，避免 `duplicate location "/"`。
- 建议路由：
  - `/` -> `127.0.0.1:5173`
  - `/api/` -> `127.0.0.1:8080/api/`

配置后执行：

```bash
nginx -t && nginx -s reload
```

## 生产更新与回滚

### 更新

```bash
cd /www/wwwroot/MyProject-SDAgent
git pull
cd deploy
docker compose --env-file .env up -d --build
```

### 快速回滚

```bash
cd /www/wwwroot/MyProject-SDAgent
git log --oneline -n 10
git checkout <稳定版本commit>
cd deploy
docker compose --env-file .env up -d --build
```

## 常见排障

- 查看后端日志：`docker compose logs -f backend`
- 查看最近错误：`docker compose logs --tail=200 backend`
- 查看容器状态：`docker compose ps`
- 查看后端健康状态：`docker inspect -f '{{.State.Health.Status}}' policy-agent-backend`
- 若知识库模型列表为空或上传失败，先检查 `backend` 日志是否有 `EmbeddingService` 相关报错，再核对 `APP_EMBEDDING_OLLAMA_BASE_URL`。

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| 健康检查 | http://localhost:8080/api/chat/health |
| MinIO API | http://localhost:9000 |
| MinIO Console | http://localhost:9001 |

## API 概览

### 对话与文档

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/chat` | 标准对话 |
| POST | `/api/chat/stream` | 流式对话（SSE） |
| GET | `/api/chat/health` | 健康检查 |
| POST | `/api/documents/load` | 加载默认文档 |
| POST | `/api/documents/load-directory` | 加载指定目录文档 |
| DELETE | `/api/documents` | 删除文档 |

### 用户认证与会话

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/auth/me` | 当前用户信息 |
| GET | `/api/conversations` | 当前用户会话列表 |
| GET | `/api/conversations/{sessionId}` | 获取/创建指定会话 |
| DELETE | `/api/conversations/{sessionId}` | 删除会话 |

### 管理员配置与知识库

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/admin/auth/login` | 管理员登录 |
| POST | `/api/admin/auth/change-password` | 修改管理员密码 |
| GET | `/api/admin/agent-config` | 获取智能体配置 |
| PUT | `/api/admin/agent-config` | 更新智能体配置 |
| POST | `/api/admin/agent-config/reset` | 重置智能体配置 |
| POST | `/api/admin/agent-config/test` | 管理员测试对话 |
| GET | `/api/admin/knowledge/folders` | 获取知识库目录树 |
| POST | `/api/admin/knowledge/documents` | 上传知识文档 |
| GET | `/api/admin/knowledge/documents` | 分页查询文档 |
| POST | `/api/admin/knowledge/documents/{id}/reingest` | 重新入库文档 |
| GET | `/api/admin/knowledge/embedding-models` | 获取可用嵌入模型 |

### 多模态

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/multimodal/transcribe` | 语音识别 |
| POST | `/api/multimodal/analyze-image` | 图像分析 |
| POST | `/api/multimodal/analyze-invoice` | 发票识别 |
| POST | `/api/multimodal/analyze-device` | 设备识别 |

## 常用命令

```bash
# backend
cd backend
./mvnw clean package
./mvnw test
./mvnw spring-boot:run

# frontend
cd frontend
npm run dev
npm run build
npm run lint
```

## 默认端口

| 服务 | 端口 |
|------|------|
| 前端 | 5173 |
| 后端 | 8080 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| MinIO API | 9000 |
| MinIO Console | 9001 |
