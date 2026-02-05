# 山东省智能政策咨询助手

基于 AI/LLM 的山东省以旧换新政策智能咨询系统，采用 RAG (检索增强生成) 技术提供准确的政策解答服务。

## 技术架构

| 层级 | 技术栈 |
|------|--------|
| 前端 | React 19 + Vite 7 |
| 后端 | Spring Boot 3.4 + Spring AI 1.0.0-M5 |
| 大模型 | 阿里云 DashScope (通义千问 qwen-plus) |
| 向量数据库 | PostgreSQL 16 + pgvector |
| 会话存储 | Redis 7 |

## 项目结构

```
├── backend/                    # 后端服务
│   ├── src/main/java/          # Java 源码
│   ├── src/main/resources/     # 配置文件
│   ├── docker-compose.yml      # 基础设施编排
│   └── pom.xml                 # Maven 依赖
├── frontend/                   # 前端应用
│   ├── src/                    # React 源码
│   ├── vite.config.js          # Vite 配置
│   └── package.json            # npm 依赖
└── data/                       # 政策文档
    ├── 2025年家电和数码以旧换新政策文件/
    └── 市级消费活动政策/
```

## 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- Docker & Docker Compose

### 1. 设置环境变量

```bash
export DASHSCOPE_API_KEY=your_api_key_here
```

### 2. 启动基础设施

```bash
cd backend
docker compose up -d
```

等待服务健康检查通过：
```bash
docker ps
# 确认 policy-agent-postgres 和 policy-agent-redis 状态为 healthy
```

### 3. 启动后端服务

```bash
cd backend
./mvnw spring-boot:run
```

### 4. 启动前端服务

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

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/chat` | 标准对话 |
| POST | `/api/chat/stream` | 流式对话 (SSE) |
| GET | `/api/chat/health` | 健康检查 |
| POST | `/api/documents/load` | 加载默认文档目录 |
| POST | `/api/documents/load-directory?path=xxx` | 加载指定目录文档 |

### 对话示例

```bash
# 标准对话
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "山东省家电以旧换新补贴政策是什么？"}'

# 流式对话
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "空调补贴最多能补几台？"}'

# 加载政策文档
curl -X POST "http://localhost:8080/api/documents/load-directory?path=/path/to/data"
```

## 配置文件

| 文件 | 路径 | 说明 |
|------|------|------|
| Spring Boot 配置 | `backend/src/main/resources/application.yml` | 数据库、AI模型、RAG参数配置 |
| Maven 依赖 | `backend/pom.xml` | 后端依赖管理 |
| Docker 编排 | `backend/docker-compose.yml` | PostgreSQL + Redis 服务 |
| Vite 配置 | `frontend/vite.config.js` | 前端构建配置、API代理 |
| npm 依赖 | `frontend/package.json` | 前端依赖管理 |
| ESLint 规则 | `frontend/eslint.config.js` | 代码规范检查 |

## 主要配置项

### 后端 (application.yml)

```yaml
# AI 模型配置
spring.ai.openai:
  base-url: https://dashscope.aliyuncs.com/compatible-mode
  api-key: ${DASHSCOPE_API_KEY}
  chat.options.model: qwen-plus
  embedding.options.model: text-embedding-v3

# 数据库连接
spring.datasource:
  url: jdbc:postgresql://localhost:5432/policy_agent
  username: postgres
  password: postgres

# Redis 连接
spring.data.redis:
  host: localhost
  port: 6379

# RAG 参数
app.rag:
  document-path: data
  chunking:
    default-chunk-size: 800
    chunk-overlap: 100
  retrieval:
    top-k: 5
    similarity-threshold: 0.7
```

### 前端 (vite.config.js)

```javascript
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

## 常用命令

### 后端

```bash
cd backend

./mvnw spring-boot:run      # 启动服务
./mvnw clean package        # 构建 JAR
./mvnw test                 # 运行测试

docker-compose up -d        # 启动数据库
docker-compose down         # 停止数据库
docker-compose logs -f      # 查看日志
```

### 前端

```bash
cd frontend

npm install                 # 安装依赖
npm run dev                 # 开发模式
npm run build               # 生产构建
npm run lint                # 代码检查
npm run preview             # 预览构建
```

## 调试
```bash
(base) lyz-ubuntu@lyz-ubuntu-OptiPlex-7080:~/Project/MyProject-SDAgent/backend$ lsof -i :8080 | grep LISTEN
java    1190980 lyz-ubuntu  278u  IPv6 2773070      0t0  TCP *:http-alt (LISTEN)
(base) lyz-ubuntu@lyz-ubuntu-OptiPlex-7080:~/Project/MyProject-SDAgent/backend$ kill 1190980 && sleep 2 && echo "Process killed"
Process killed
```

## 服务端口

| 服务 | 端口 |
|------|------|
| 前端开发服务器 | 5173 |
| 后端 API | 8080 |
| PostgreSQL | 5432 |
| Redis | 6379 |
