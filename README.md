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
