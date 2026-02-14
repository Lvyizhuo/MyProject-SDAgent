# 山东省智能政策咨询助手

基于 AI/LLM 的山东省以旧换新政策智能咨询系统，采用 RAG（检索增强生成）技术提供政策问答、补贴计算与多模态识别能力。

## 技术架构

| 层级 | 技术栈 |
|------|--------|
| 前端 | React 19 + Vite 7 |
| 后端 | Spring Boot 3.4 + Spring AI 1.0.3 |
| 大模型 | 阿里云 DashScope（通义千问 `qwen3-max`） |
| 向量数据库 | PostgreSQL 16 + pgvector |
| 会话存储 | Redis 7 |
| 鉴权 | Spring Security + JWT |

## 项目结构

```text
├── backend/                    # 后端服务
│   ├── src/main/java/com/shandong/policyagent/
│   │   ├── advisor/            # 安全、重读校验、日志、会话记忆
│   │   ├── config/             # Spring 配置（ChatClient/Security 等）
│   │   ├── controller/         # REST API 控制器
│   │   ├── multimodal/         # 语音识别与视觉分析
│   │   ├── rag/                # 文档切片、检索、向量存储
│   │   ├── security/           # JWT 相关
│   │   ├── service/            # 业务服务
│   │   └── tool/               # LLM 工具（补贴计算/文件解析/联网搜索）
│   ├── src/main/resources/     # 配置文件
│   ├── docker-compose.yml      # PostgreSQL + Redis
│   └── pom.xml
├── frontend/                   # 前端应用
│   ├── src/
│   ├── vite.config.js
│   └── package.json
└── data/                       # 政策文档与增量状态
```

## 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- Docker & Docker Compose

### 1. 设置环境变量

```bash
export DASHSCOPE_API_KEY=your_dashscope_api_key
# 可选：启用联网搜索工具
export TAVILY_API_KEY=your_tavily_api_key
```

### 2. 启动基础设施

```bash
cd backend
docker compose up -d
```

### 3. 启动后端

```bash
cd backend
# 默认模式
./mvnw spring-boot:run

# 启用 mcp profile
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
| 前端界面 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| 健康检查 | http://localhost:8080/api/chat/health |

## API 接口

### 对话与文档

| 方法 | 路径 | 描述 | 鉴权 |
|------|------|------|------|
| POST | `/api/chat` | 标准对话（完整响应） | 可匿名 |
| POST | `/api/chat/stream` | 流式对话（SSE） | 可匿名 |
| GET | `/api/chat/health` | 健康检查 | 可匿名 |
| POST | `/api/documents/load` | 加载默认文档目录 | 可匿名 |
| POST | `/api/documents/load-directory?path=xxx` | 加载指定目录文档 | 可匿名 |
| DELETE | `/api/documents?ids=id1&ids=id2` | 删除向量库文档 | 可匿名 |

### 认证与会话

| 方法 | 路径 | 描述 | 鉴权 |
|------|------|------|------|
| POST | `/api/auth/register` | 用户注册 | 可匿名 |
| POST | `/api/auth/login` | 用户登录 | 可匿名 |
| GET | `/api/auth/me` | 获取当前用户信息 | 需要 JWT |
| GET | `/api/conversations` | 获取当前用户会话列表 | 需要 JWT |
| GET | `/api/conversations/{sessionId}` | 获取/创建指定会话 | 需要 JWT |
| DELETE | `/api/conversations/{sessionId}` | 删除指定会话 | 需要 JWT |

### 多模态

| 方法 | 路径 | 描述 | 鉴权 |
|------|------|------|------|
| POST | `/api/multimodal/transcribe` | 语音转文字 | 可匿名 |
| POST | `/api/multimodal/analyze-image` | 通用图片理解 | 可匿名 |
| POST | `/api/multimodal/analyze-invoice` | 发票识别 | 可匿名 |
| POST | `/api/multimodal/analyze-device` | 家电/设备识别 | 可匿名 |

## 对话示例

```bash
# 标准对话
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "山东省家电以旧换新补贴政策是什么？"}'

# 流式对话
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "空调补贴最多能补几台？"}'

# 加载文档
curl -X POST "http://localhost:8080/api/documents/load-directory?path=/path/to/data"
```

## 配置文件

| 文件 | 路径 | 说明 |
|------|------|------|
| Spring Boot 配置 | `backend/src/main/resources/application.yml` | 数据源、AI 模型、RAG、工具开关 |
| Maven 依赖 | `backend/pom.xml` | 后端依赖管理 |
| Docker 编排 | `backend/docker-compose.yml` | PostgreSQL + Redis |
| Vite 配置 | `frontend/vite.config.js` | 前端构建与代理 |
| npm 依赖 | `frontend/package.json` | 前端依赖与脚本 |
| ESLint 规则 | `frontend/eslint.config.js` | 前端代码检查规则 |

## 常用命令

### 后端

```bash
cd backend

./mvnw spring-boot:run
./mvnw clean package
./mvnw test

docker compose up -d
docker compose down
docker compose logs -f
```

### 前端

```bash
cd frontend

npm install
npm run dev
npm run build
npm run lint
npm run preview
```

## 服务端口

| 服务 | 端口 |
|------|------|
| 前端开发服务器 | 5173 |
| 后端 API | 8080 |
| PostgreSQL | 5432 |
| Redis | 6379 |
